import {describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {factorResearchHandoff} from '../../src/features/research/factor-research-handoff';
import {factorResearchApi} from '../../src/features/research/api/factor-research';
import zh from '../../src/i18n/locales/zh-CN/pages.json';
import en from '../../src/i18n/locales/en-US/pages.json';

const first = '924e94e3-3e04-4c0a-82ac-45c91f66c2da';
const second = 'cd005343-d537-445c-9ca6-8158456deb6a';
const version = '68af4581-bc09-4bd8-9137-97ca12a7463a';
const frozenAt = '2026-10-31T08:06:22.202834704Z';
const input = (id = first, time: unknown = frozenAt, inputVersion = 'HISTORICAL_RESEARCH_INPUT_V2') => new Blob([JSON.stringify({
    schemaVersion: 'frozen-research-export.v1', dataset: {datasetId: id,
        frozenInput: {datasetVersionId: id === first ? version : 'bc3b0488-d2d2-42a3-9713-8223c60f2492', frozenAt: time, version: inputVersion}},
})]);

describe('offline factor research handoff', () => {
    it('distinguishes Dataset/version paths and retains every explicit reproduction parameter', async () => {
        const a = await factorResearchHandoff(input(), first), b = await factorResearchHandoff(input(second), second);
        expect(a.inputName).not.toBe(b.inputName); expect(a.directory).not.toBe(b.directory);
        expect(a.command).not.toBe(b.command);
        expect(a.inputName).toContain(version); expect(a.command).toContain(`--created-at "${frozenAt}"`);
        for (const flag of ['.\\.venv\\Scripts\\python.exe', '--factor-window 24', '--horizon-bars 6', '--seed 42', '--bootstrap-repetitions 200'])
            expect(a.command).toContain(flag);
        expect(a.command).toContain('Test-Path -LiteralPath'); expect(a.command).toContain('OUTPUT_ALREADY_EXISTS');
        expect(a.command).toContain(`.\\${a.directory}\\${a.inputName}`);
        expect(a.command).toContain(`.\\${a.directory}\\${a.outputName}`);
        expect(('C:\\Users\\researcher\\Downloads\\NexusQuant-Python-Research-Companion-1.0.3-windows-amd64-py311\\'+a.directory+'\\'+a.inputName).length).toBeLessThan(260);
    });
    it.each([null, '', 'unknown', '2026-02-30T08:06:22Z', '2026-10-31T08:06:22Z";Remove-Item x'])('does not invent a timestamp for %s', async time => {
        const result = await factorResearchHandoff(input(first, time), first);
        expect(result.createdAt).toBeNull(); expect(result.command).toBeNull(); expect(result.outputName).toBeNull();
    });
    it('leaves an omitted frozenAt unavailable', async () => {
        const blob = new Blob([JSON.stringify({schemaVersion: 'frozen-research-export.v1', dataset: {datasetId: first,
            frozenInput: {datasetVersionId: version, version: 'HISTORICAL_RESEARCH_INPUT_V2'}}})]);
        expect((await factorResearchHandoff(blob, first)).command).toBeNull();
    });
    it('rejects mismatched identity, legacy input, unsafe version paths and oversized downloads', async () => {
        await expect(factorResearchHandoff(input(), second)).rejects.toThrow('IDENTITY_INVALID');
        await expect(factorResearchHandoff(input(first, frozenAt, 'HISTORICAL_RESEARCH_INPUT_V1'), first)).rejects.toThrow('IDENTITY_INVALID');
        await expect(factorResearchHandoff(new Blob([JSON.stringify({schemaVersion: 'frozen-research-export.v1',
            dataset: {datasetId: first, frozenInput: {version: 'HISTORICAL_RESEARCH_INPUT_V2', datasetVersionId: '../unsafe'}}})]), first)).rejects.toThrow('IDENTITY_INVALID');
        await expect(factorResearchHandoff(new Blob([' '.repeat(8 * 1024 * 1024 + 1)]), first)).rejects.toThrow('IDENTITY_INVALID');
    });
    it('keeps the original server preview identity guard and never posts on download', async () => {
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: input()});
        const post = vi.spyOn(apiClient, 'post').mockResolvedValue({data: {schemaVersion: 'python-factor-research-artifact.v1', dataset: {datasetId: second}, factors: [{}, {}, {}]}});
        try {
            const signal = new AbortController().signal;
            await factorResearchHandoff(await factorResearchApi.download(first, signal), first);
            expect(get).toHaveBeenCalledWith(`/marketdata/datasets/${first}/factor-research/input`, {signal, responseType: 'blob'});
            expect(post).not.toHaveBeenCalled();
            await expect(factorResearchApi.preview(first, '{}', signal)).rejects.toThrow('FACTOR_PREVIEW_IDENTITY_INVALID');
        } finally {vi.restoreAllMocks();}
    });
    it('keeps bilingual guidance and discloses standalone installation and identity changes', () => {
        expect(Object.keys(zh.factorResearch).sort()).toEqual(Object.keys(en.factorResearch).sort());
        expect(zh.factorResearch.instructions).not.toContain('research/py');
        expect(en.factorResearch.instructions).not.toContain('research/py');
        expect(zh.factorResearch.installMeaning).toContain('requirements.lock');
        expect(en.factorResearch.repeatMeaning).toContain('Artifact ID and checksum change');
    });
});
