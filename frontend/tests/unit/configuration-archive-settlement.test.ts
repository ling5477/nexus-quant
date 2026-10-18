import {it, expect, vi} from 'vitest';
const fixture = vi.hoisted(() => ({invalidate: vi.fn(() => new Promise(() => {}))}));
vi.mock('@tanstack/react-query', () => ({
    useQuery: vi.fn(),
    useMutation: (options: unknown) => options,
    useQueryClient: () => ({invalidateQueries: fixture.invalidate}),
}));
import {useArchiveResearchMutation} from '@/features/research/hooks/useResearchListQuery';
import {useArchiveBacktestMutation} from '@/features/backtests/useBacktestsListQuery';
// 故意永不结束的缓存重读不能使归档失败的确认框继续loading。
it.each([useArchiveResearchMutation, useArchiveBacktestMutation])(
    'settles archive without waiting for unrelated cached reads',
    (hook) => {
        const mutation = hook() as unknown as {onSettled: () => unknown};
        expect(mutation.onSettled()).toBeUndefined();
        expect(fixture.invalidate).toHaveBeenCalled();
    },
);
