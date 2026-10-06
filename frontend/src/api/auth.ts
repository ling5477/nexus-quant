import {apiClient} from '@/api/client';
import type {CurrentUser, LoginRequest, LoginResponse} from '@/types/auth';

export const authApi = {
    async login(payload: LoginRequest): Promise<LoginResponse> {
        const {data} = await apiClient.post<LoginResponse>('/auth/login', payload);
        return data;
    },
    async changePassword(payload: {currentPassword: string; newPassword: string}): Promise<void> {
        await apiClient.post('/auth/change-password', payload);
    },
    async logout(): Promise<void> {
        await apiClient.post('/auth/logout');
    },
    async getCurrentUser(): Promise<CurrentUser> {
        const {data} = await apiClient.get<CurrentUser>('/auth/me');
        return data;
    },
};
