package com.guidinglight.nexusquant.research.infra;

import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 在数据库查询内隔离私有费用来源及其派生对象，避免列表泄漏和逐行额外查询。 */
@Component
public class ResearchFeeReadScope {
    private final ResearchFeeSourcePort source;

    @Autowired
    public ResearchFeeReadScope(ObjectProvider<ResearchFeeSourcePort> provider) {
        this(provider.getIfAvailable(ResearchFeeSourcePort::disabled));
    }

    public ResearchFeeReadScope(ResearchFeeSourcePort source) {
        this.source = source;
    }

    /** SQL表达式仅由仓储常量提供；身份是服务端Long，不能从请求拼接任意字符串。 */
    public String configuration(String expression) {
        return configuration(expression, source.currentReadOwner());
    }

    private String configuration(String expression, Long owner) {
        if (owner == null) return "TRUE";
        String spec = "(" + expression + "->'executionSpec')";
        String mode = "COALESCE(" + spec + "->>'costSource','')";
        // 来源身份被删除或模式被篡改时默认拒绝；旧无私有字段的研究假设保留既有可见性。
        return "(("
                + mode
                + " <> 'CURRENT_ACCOUNT_OBSERVATION' AND NOT COALESCE("
                + spec
                + " ??| ARRAY['feeObservationId','feeAccountId','feeTradeEnv','feeSymbol',"
                + "'feeSourceOwnerUserId','feeSourceSnapshotJson','feeSourceSnapshotSha256'], FALSE)) OR ("
                + mode
                + " = 'CURRENT_ACCOUNT_OBSERVATION' AND "
                + spec
                + "->>'feeSourceOwnerUserId' = '"
                + owner
                + "'))";
    }

    /** 评估与发布按不可变来源运行关联隔离，不依赖调用方提供的所有者副本。 */
    public String backtest(String runIdExpression) {
        return backtest(runIdExpression, source.currentReadOwner());
    }

    /** 已有所有者查询使用服务端传入的用户身份，不能因脱离HTTP请求而省略费用来源隔离。 */
    public String backtest(String runIdExpression, Long owner) {
        if (owner == null) return "TRUE";
        return "EXISTS (SELECT 1 FROM backtest_runs nq_fee_run WHERE nq_fee_run.backtest_run_id = "
                + runIdExpression
                + " AND "
                + configuration("nq_fee_run.config_snapshot_json", owner)
                + ")";
    }

    /** SIM即使自带配置省略来源字段，也必须沿原发布记录继承私有范围。 */
    public String paper() {
        if (source.currentReadOwner() == null) return "TRUE";
        return "EXISTS (SELECT 1 FROM backtest_publish_records nq_fee_publish WHERE nq_fee_publish.publish_record_id = "
                + "paper_trading_runs.publish_id AND "
                + backtest("nq_fee_publish.backtest_run_id")
                + ")";
    }
}
