import {useEffect, useRef, useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useAuthStore} from '@/store/auth-store';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {simAnalysisApi, type SimAnalysisRun} from '../api/analysis';
import {observationsApi, type EvaluationWindow, type ObservationCreateRequest, type ObservationReviewRequest} from '../api/observations';
import {readObservationOperation, storeObservationOperation, executeObservationOperation, observationOperationChanged,
    clearObservationOperation, type PendingObservationOperation} from '../observation-operations';
import type {AppApiError} from '@/types/api';

export function useForwardSimObservations(run: SimAnalysisRun, range: EvaluationWindow | null, observationId: string | null,
    reviewId: string | null, offset: number, reviewsOffset: number, onSaved: (observationId: string, reviewId?: string) => void) {
    const owner = useAuthStore(state => state.currentUser?.userId);
    const cache = useQueryClient();
    const [stored, setStored] = useState<{owner?: number; run: string; pending: PendingObservationOperation | null; storageError: boolean}>({owner, run: run.paperRunId, pending: null, storageError: false});
    const scope = useRef({owner, run: run.paperRunId, active: true});
    scope.current.owner = owner; scope.current.run = run.paperRunId;
    useEffect(() => {scope.current.active = true; return () => {scope.current.active = false;};}, []);
    useEffect(() => {
        const refresh = () => {
            try {setStored({owner, run: run.paperRunId, pending: owner ? readObservationOperation(owner, run.paperRunId) : null, storageError: false});}
            catch {setStored({owner, run: run.paperRunId, pending: null, storageError: true});}
        };
        refresh(); window.addEventListener(observationOperationChanged, refresh); window.addEventListener('storage', refresh);
        return () => {window.removeEventListener(observationOperationChanged, refresh); window.removeEventListener('storage', refresh);};
    }, [owner, run.paperRunId]);
    const rawPerformance = useQuery({queryKey: paperTradingQueryKeys.performance(owner, run.paperRunId, null, null),
        queryFn: ({signal}) => simAnalysisApi.performance(run.paperRunId, null, null, signal), enabled: !!owner && !range && !reviewId,
        retry: false, refetchInterval: 30_000});
    const evaluation = useQuery({queryKey: paperTradingQueryKeys.forwardEvaluation(owner, run.paperRunId, range),
        queryFn: ({signal}) => observationsApi.evaluate(run, range!, signal), enabled: !!owner && !!range && !reviewId, retry: false});
    const list = useQuery({queryKey: paperTradingQueryKeys.observations(owner, run.paperRunId, offset),
        queryFn: ({signal}) => observationsApi.list(run, offset, signal), enabled: !!owner, retry: false});
    const detail = useQuery({queryKey: paperTradingQueryKeys.observation(owner, run.paperRunId, observationId, reviewsOffset),
        queryFn: ({signal}) => observationsApi.detail(observationId!, run, signal, reviewsOffset), enabled: !!owner && !!observationId, retry: false});
    const review = useQuery({queryKey: paperTradingQueryKeys.observationReview(owner, run.paperRunId, observationId, reviewId),
        queryFn: ({signal}) => observationsApi.review(observationId!, reviewId!, run, signal), enabled: !!owner && !!observationId && !!reviewId, retry: false});
    const mutation = useMutation({mutationFn: async (action: {kind: 'OBSERVATION'; input: ObservationCreateRequest}
        | {kind: 'OBSERVATION_REVIEW'; observationId: string; input: ObservationReviewRequest}
        | {kind: 'QUERY'; original: PendingObservationOperation}
        | {kind: 'RESEND'; original: PendingObservationOperation}) => {
        if (!owner) throw new Error('OBSERVATION_OPERATION_SCOPE_CHANGED');
        let original: PendingObservationOperation;
        if (action.kind === 'QUERY' || action.kind === 'RESEND') original = action.original;
        else {
            if (readObservationOperation(owner, run.paperRunId)) throw new Error('OBSERVATION_OPERATION_PENDING');
            original = action.kind === 'OBSERVATION'
                ? {operationId: crypto.randomUUID(), paperRunId: run.paperRunId, kind: 'OBSERVATION', parentKey: run.paperRunId, input: action.input}
                : {operationId: crypto.randomUUID(), paperRunId: run.paperRunId, kind: 'OBSERVATION_REVIEW', parentKey: action.observationId, input: action.input};
        }
        const current = () => scope.current.active && scope.current.owner === owner && scope.current.run === run.paperRunId;
        try {
            const result = await executeObservationOperation(owner, run, original, action.kind !== 'QUERY', current,
                action.kind === 'OBSERVATION' || action.kind === 'OBSERVATION_REVIEW');
            if ('observation' in result) onSaved(result.observation.observationId);
            else onSaved(result.review.observationId, result.review.reviewId);
            await cache.invalidateQueries({queryKey: paperTradingQueryKeys.observationRoot(owner, run.paperRunId)});
            return result;
        } catch (error) {
            if (current() && action.kind !== 'QUERY' && [400, 403, 404, 422].includes((error as AppApiError).status))
                storeObservationOperation(owner, run.paperRunId, {...original, rejected: true});
            throw error;
        }
    }});
    const discardRejected = () => {
        if (owner && stored.pending?.rejected) clearObservationOperation(owner, run.paperRunId, stored.pending.operationId);
    };
    const visibleStorage = stored.owner === owner && stored.run === run.paperRunId ? stored : {pending: null, storageError: false};
    return {rawPerformance, evaluation, list, detail, review, mutation, pending: visibleStorage.pending, storageError: visibleStorage.storageError, discardRejected};
}
