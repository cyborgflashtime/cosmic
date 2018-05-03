package com.cloud.agent;

import com.cloud.legacymodel.communication.command.StartupCommand;
import com.cloud.legacymodel.exceptions.ConnectionException;
import com.cloud.utils.component.Adapter;

/**
 * AgentManager knows about the agent that's connecting.
 */
public interface StartupCommandProcessor extends Adapter {

    /**
     * This method is called by AgentManager when an agent made a
     * connection to this server before the AgentManager knows about this agent
     *
     * @param agentId id of the agent
     * @param cmd     command sent by the agent to the server on startup.
     * @return true if handled by the creator
     * @throws ConnectionException if host has problems
     */
    boolean processInitialConnect(StartupCommand[] cmd) throws ConnectionException;
}
