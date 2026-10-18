import {describe, it, expect} from 'vitest';
import {trustedNewsUrl} from '../../src/features/news/public-news';

describe('public news original-article links', () => {
    it('rejects executable, credential-bearing and publisher-lookalike URLs', () => {
        for (const value of [
            'javascript:alert(1)',
            'data:text/html,fixture',
            'http://www.coindesk.com/a',
            'https://www.coindesk.com.evil.invalid/a',
            'https://www.coindesk.com@evil.invalid/a',
            'https://fixture@www.coindesk.com/a',
            'https://www.coindesk.com:8443/a',
            '//www.coindesk.com/a',
        ])
            expect(trustedNewsUrl(value)).toBeNull();
    });
    it('keeps a real publisher HTTPS article location', () => {
        expect(trustedNewsUrl('https://www.coindesk.com/markets/2026/10/13/fixture')).toBe(
            'https://www.coindesk.com/markets/2026/10/13/fixture',
        );
    });
});
