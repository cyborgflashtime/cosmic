package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.DeleteStoragePoolCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.hypervisor.kvm.storage.KvmStoragePoolManager;
import com.cloud.legacymodel.exceptions.CloudRuntimeException;
import com.cloud.legacymodel.to.StorageFilerTO;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

@ResourceWrapper(handles = DeleteStoragePoolCommand.class)
public final class LibvirtDeleteStoragePoolCommandWrapper
        extends CommandWrapper<DeleteStoragePoolCommand, Answer, LibvirtComputingResource> {

    @Override
    public Answer execute(final DeleteStoragePoolCommand command,
                          final LibvirtComputingResource libvirtComputingResource) {
        try {
            final StorageFilerTO pool = command.getPool();
            final KvmStoragePoolManager storagePoolMgr = libvirtComputingResource.getStoragePoolMgr();
            storagePoolMgr.deleteStoragePool(pool.getType(), pool.getUuid());
            return new Answer(command);
        } catch (final CloudRuntimeException e) {
            return new Answer(command, false, e.toString());
        }
    }
}
