import {apiClient} from '@/api/client';
import type {ScheduledJobControl, ScheduledJobPatch, ScheduledJobRunResult} from '@/features/scheduler/types';

export const schedulerApi = {
    async list(): Promise<ScheduledJobControl[]> {
        const {data} = await apiClient.get<ScheduledJobControl[]>('/scheduler/jobs');
        return data;
    },
    async patch(jobKey: string, request: ScheduledJobPatch): Promise<ScheduledJobControl> {
        const {data} = await apiClient.patch<ScheduledJobControl>(
            `/scheduler/jobs/${encodeURIComponent(jobKey)}`, request);
        return data;
    },
    async runOnce(jobKey: string): Promise<ScheduledJobRunResult> {
        const {data} = await apiClient.post<ScheduledJobRunResult>(
            `/scheduler/jobs/${encodeURIComponent(jobKey)}/run-once`);
        return data;
    },
};
