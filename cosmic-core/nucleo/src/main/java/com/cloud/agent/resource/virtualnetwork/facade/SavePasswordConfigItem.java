package com.cloud.agent.resource.virtualnetwork.facade;

import com.cloud.agent.resource.virtualnetwork.ConfigItem;
import com.cloud.agent.resource.virtualnetwork.VRScripts;
import com.cloud.agent.resource.virtualnetwork.model.VmPassword;
import com.cloud.legacymodel.communication.command.NetworkElementCommand;
import com.cloud.legacymodel.communication.command.SavePasswordCommand;

import java.util.List;

public class SavePasswordConfigItem extends AbstractConfigItemFacade {

    @Override
    public List<ConfigItem> generateConfig(final NetworkElementCommand cmd) {
        final SavePasswordCommand command = (SavePasswordCommand) cmd;
        final VmPassword vmPassword = new VmPassword(command.getVmIpAddress(), command.getPassword());

        return generateConfigItems(vmPassword);
    }

    @Override
    protected List<ConfigItem> generateConfigItems(final Object configuration) {
        destinationFile = VRScripts.VM_PASSWORD_CONFIG;

        return super.generateConfigItems(configuration);
    }
}
