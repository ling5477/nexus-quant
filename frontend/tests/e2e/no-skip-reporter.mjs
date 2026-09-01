export default class NoSkipReporter {
    constructor() {
        this.skipped = [];
        this.executed = 0;
    }

    onTestEnd(test, result) {
        if (result.status === 'skipped') {
            this.skipped.push(test.titlePath().join(' › '));
            return;
        }
        this.executed += 1;
    }

    async onEnd(result) {
        if (this.skipped.length > 0) {
            console.error(`CRITICAL_E2E_SKIP_FORBIDDEN count=${this.skipped.length}`);
            for (const title of this.skipped) {
                console.error(`  skipped=${title}`);
            }
            return {status: 'failed'};
        }
        console.info(`CRITICAL_E2E_NO_SKIP executed=${this.executed} status=${result.status}`);
        return {status: result.status};
    }
}
