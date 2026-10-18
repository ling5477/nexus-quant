import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {preferencesApi, type UserPreferences} from '@/features/preferences/api/preferences';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';

export function useUserPreferences() {
    const userId = useAuthStore((state) => state.currentUser?.userId ?? 0);
    const token = useAuthStore((state) => state.accessToken);
    const mustChangePassword = useAuthStore((state) => state.currentUser?.mustChangePassword);
    const client = useQueryClient();
    const key = ['user-preferences', userId] as const;
    const query = useQuery<UserPreferences, AppApiError>({
        queryKey: key,
        queryFn: ({signal}) => preferencesApi.read(token!, signal),
        enabled: Boolean(token && userId > 0 && !mustChangePassword),
    });
    const save = useMutation<
        UserPreferences,
        AppApiError,
        Partial<UserPreferences> & {expectedVersion?: number}
    >({
        mutationFn: (patch) => {
            if (!query.data || !token || useAuthStore.getState().currentUser?.userId !== userId)
                return Promise.reject(
                    new Error('Read the current user preferences before saving.'),
                );
            // 只提交已读版本；409 时保留用户选择，要求重新读取，不自动覆盖另一会话。
            const {expectedVersion, ...value} = patch;
            return preferencesApi.save(
                {...query.data, ...value, version: expectedVersion ?? query.data.version},
                token,
            );
        },
        onSuccess: (value) => {
            if (useAuthStore.getState().currentUser?.userId === userId)
                client.setQueryData(key, value);
        },
    });
    return {query, save};
}
