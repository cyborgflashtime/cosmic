package com.cloud.hypervisor.xenserver.resource.wrapper.xenbase;

import com.cloud.hypervisor.xenserver.resource.CitrixResourceBase;
import com.cloud.legacymodel.communication.answer.Answer;
import com.cloud.legacymodel.communication.answer.GetVmDiskStatsAnswer;
import com.cloud.legacymodel.communication.command.GetVmDiskStatsCommand;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

@ResourceWrapper(handles = GetVmDiskStatsCommand.class)
public final class CitrixGetVmDiskStatsCommandWrapper extends CommandWrapper<GetVmDiskStatsCommand, Answer, CitrixResourceBase> {

    @Override
    public Answer execute(final GetVmDiskStatsCommand command, final CitrixResourceBase citrixResourceBase) {
        return new GetVmDiskStatsAnswer(command, null, null, null);
    }
}
