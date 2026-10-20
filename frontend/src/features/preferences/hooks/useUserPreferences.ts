import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {preferencesApi, type UserPreferences} from '@/features/preferences/api/preferences';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import {CanceledError} from 'axios';

export function useUserPreferences() {
    const userId = useAuthStore((state) => state.currentUser?.userId ?? 0);
    const token = useAuthStore(selectSessionIdentity);
    const mustChangePassword = useAuthStore((state) => state.currentUser?.mustChangePassword);
    const client = useQueryClient();
    const key = ['user-preferences', userId] as const;
    const query = useQuery<UserPreferences, AppApiError>({
        queryKey: key,
        queryFn: ({signal}) => {
            const current = useAuthStore.getState();
            if (selectSessionIdentity(current) !== token || !current.accessToken)
                throw new CanceledError('Session changed before reading preferences.');
            return preferencesApi.read(current.accessToken, signal);
        },
        enabled: Boolean(token && userId > 0 && !mustChangePassword),
    });
    const save = useMutation<
        UserPreferences,
        AppApiError,
        Partial<UserPreferences> & {expectedVersion?: number}
    >({
        mutationFn: (patch) => {
            const current = useAuthStore.getState();
            if (!query.data || !token || current.currentUser?.userId !== userId
                || selectSessionIdentity(current) !== token || !current.accessToken)
                return Promise.reject(
                    new Error('Read the current user preferences before saving.'),
                );
            // 只提交已读版本；409 时保留用户选择，要求重新读取，不自动覆盖另一会话。
            const {expectedVersion, ...value} = patch;
            return preferencesApi.save(
                {...query.data, ...value, version: expectedVersion ?? query.data.version},
                current.accessToken,
            );
        },
        onSuccess: (value) => {
            if (useAuthStore.getState().currentUser?.userId === userId)
                client.setQueryData(key, value);
        },
    });
    return {query, save};
}
