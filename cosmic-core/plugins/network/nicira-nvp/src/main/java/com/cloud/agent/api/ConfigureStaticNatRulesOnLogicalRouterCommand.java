package com.cloud.agent.api;

import com.cloud.legacymodel.communication.command.Command;
import com.cloud.legacymodel.to.StaticNatRuleTO;

import java.util.List;

/**
 *
 */
public class ConfigureStaticNatRulesOnLogicalRouterCommand extends Command {

    private String logicalRouterUuid;
    private List<StaticNatRuleTO> rules;

    public ConfigureStaticNatRulesOnLogicalRouterCommand(final String logicalRouterUuid, final List<StaticNatRuleTO> rules) {
        super();
        this.logicalRouterUuid = logicalRouterUuid;
        this.rules = rules;
    }

    public String getLogicalRouterUuid() {
        return logicalRouterUuid;
    }

    public void setLogicalRouterUuid(final String logicalRouterUuid) {
        this.logicalRouterUuid = logicalRouterUuid;
    }

    public List<StaticNatRuleTO> getRules() {
        return rules;
    }

    public void setRules(final List<StaticNatRuleTO> rules) {
        this.rules = rules;
    }

    /* (non-Javadoc)
     * @see com.cloud.legacymodel.communication.command.Command#executeInSequence()
     */
    @Override
    public boolean executeInSequence() {
        return false;
    }
}
