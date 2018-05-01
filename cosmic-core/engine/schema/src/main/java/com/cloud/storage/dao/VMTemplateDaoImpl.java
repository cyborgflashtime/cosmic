package com.cloud.storage.dao;

import com.cloud.dc.dao.DataCenterDao;
import com.cloud.domain.dao.DomainDao;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.legacymodel.exceptions.CloudRuntimeException;
import com.cloud.model.enumeration.HypervisorType;
import com.cloud.model.enumeration.ImageFormat;
import com.cloud.server.ResourceTag.ResourceObjectType;
import com.cloud.storage.Storage;
import com.cloud.storage.Storage.TemplateType;
import com.cloud.storage.VMTemplateDetailVO;
import com.cloud.storage.VMTemplateStorageResourceAssoc;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.VMTemplateZoneVO;
import com.cloud.storage.datastore.db.TemplateDataStoreDao;
import com.cloud.storage.datastore.db.TemplateDataStoreVO;
import com.cloud.tags.ResourceTagVO;
import com.cloud.tags.dao.ResourceTagDao;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.GenericSearchBuilder;
import com.cloud.utils.db.JoinBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Func;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.db.UpdateBuilder;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VMTemplateDaoImpl extends GenericDaoBase<VMTemplateVO, Long> implements VMTemplateDao {
    private static final Logger s_logger = LoggerFactory.getLogger(VMTemplateDaoImpl.class);
    protected SearchBuilder<VMTemplateVO> TemplateNameSearch;
    protected SearchBuilder<VMTemplateVO> UniqueNameSearch;
    protected SearchBuilder<VMTemplateVO> tmpltTypeSearch;
    protected SearchBuilder<VMTemplateVO> tmpltTypeHyperSearch;
    protected SearchBuilder<VMTemplateVO> readySystemTemplateSearch;
    protected SearchBuilder<VMTemplateVO> tmpltTypeHyperSearch2;
    protected SearchBuilder<VMTemplateVO> AccountIdSearch;
    protected SearchBuilder<VMTemplateVO> NameSearch;
    protected SearchBuilder<VMTemplateVO> TmpltsInZoneSearch;
    protected SearchBuilder<VMTemplateVO> ActiveTmpltSearch;
    @Inject
    VMTemplateZoneDao _templateZoneDao;
    @Inject
    VMTemplateDetailsDao _templateDetailsDao;
    @Inject
    HostDao _hostDao;
    @Inject
    DomainDao _domainDao;
    @Inject
    DataCenterDao _dcDao;
    @Inject
    TemplateDataStoreDao _templateDataStoreDao;
    @Inject
    ResourceTagDao _tagsDao;
    private SearchBuilder<VMTemplateVO> PublicSearch;
    private SearchBuilder<VMTemplateVO> NameAccountIdSearch;
    private SearchBuilder<VMTemplateVO> PublicIsoSearch;
    private SearchBuilder<VMTemplateVO> UserIsoSearch;
    private GenericSearchBuilder<VMTemplateVO, Long> CountTemplatesByAccount;
    // private SearchBuilder<VMTemplateVO> updateStateSearch;
    private SearchBuilder<VMTemplateVO> AllFieldsSearch;
    private String routerTmpltName;
    private String consoleProxyTmpltName;

    public VMTemplateDaoImpl() {
    }

    @Override
    public List<VMTemplateVO> listByPublic() {
        final SearchCriteria<VMTemplateVO> sc = PublicSearch.create();
        sc.setParameters("public", 1);
        return listBy(sc);
    }

    @Override
    public VMTemplateVO findByName(final String templateName) {
        final SearchCriteria<VMTemplateVO> sc = UniqueNameSearch.create();
        sc.setParameters("uniqueName", templateName);
        return findOneIncludingRemovedBy(sc);
    }

    @Override
    public VMTemplateVO findByTemplateName(final String templateName) {
        final SearchCriteria<VMTemplateVO> sc = NameSearch.create();
        sc.setParameters("name", templateName);
        return findOneIncludingRemovedBy(sc);
    }

    @Override
    public List<VMTemplateVO> listAllSystemVMTemplates() {
        final SearchCriteria<VMTemplateVO> sc = tmpltTypeSearch.create();
        sc.setParameters("templateType", Storage.TemplateType.SYSTEM);

        final Filter filter = new Filter(VMTemplateVO.class, "id", false, null, null);
        return listBy(sc, filter);
    }

    @Override
    public List<VMTemplateVO> listDefaultBuiltinTemplates() {
        final SearchCriteria<VMTemplateVO> sc = tmpltTypeSearch.create();
        sc.setParameters("templateType", Storage.TemplateType.BUILTIN);
        sc.setParameters("state", VirtualMachineTemplate.State.Active);
        return listBy(sc);
    }

    @Override
    public String getRoutingTemplateUniqueName() {
        return routerTmpltName;
    }

    @Override
    public List<VMTemplateVO> findIsosByIdAndPath(final Long domainId, final Long accountId, final String path) {
        final SearchCriteria<VMTemplateVO> sc = createSearchCriteria();
        sc.addAnd("iso", SearchCriteria.Op.EQ, true);
        if (domainId != null) {
            sc.addAnd("domainId", SearchCriteria.Op.EQ, domainId);
        }
        if (accountId != null) {
            sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
        }
        if (path != null) {
            sc.addAnd("path", SearchCriteria.Op.EQ, path);
        }
        return listIncludingRemovedBy(sc);
    }

    @Override
    public List<VMTemplateVO> listReadyTemplates() {
        final SearchCriteria<VMTemplateVO> sc = createSearchCriteria();
        sc.addAnd("ready", SearchCriteria.Op.EQ, true);
        sc.addAnd("format", SearchCriteria.Op.NEQ, ImageFormat.ISO);
        return listIncludingRemovedBy(sc);
    }

    @Override
    public List<VMTemplateVO> listByAccountId(final long accountId) {
        final SearchCriteria<VMTemplateVO> sc = AccountIdSearch.create();
        sc.setParameters("accountId", accountId);
        sc.setParameters("state", VirtualMachineTemplate.State.Active);
        return listBy(sc);
    }

    @Override
    @DB
    public long addTemplateToZone(final VMTemplateVO tmplt, final long zoneId) {
        final TransactionLegacy txn = TransactionLegacy.currentTxn();
        txn.start();
        final VMTemplateVO tmplt2 = findById(tmplt.getId());
        if (tmplt2 == null) {
            if (persist(tmplt) == null) {
                throw new CloudRuntimeException("Failed to persist the template " + tmplt);
            }

            if (tmplt.getDetails() != null) {
                final List<VMTemplateDetailVO> details = new ArrayList<>();
                for (final String key : tmplt.getDetails().keySet()) {
                    details.add(new VMTemplateDetailVO(tmplt.getId(), key, tmplt.getDetails().get(key), true));
                }
                _templateDetailsDao.saveDetails(details);
            }
        }
        VMTemplateZoneVO tmpltZoneVO = _templateZoneDao.findByZoneTemplate(zoneId, tmplt.getId());
        if (tmpltZoneVO == null) {
            tmpltZoneVO = new VMTemplateZoneVO(zoneId, tmplt.getId(), new Date());
            _templateZoneDao.persist(tmpltZoneVO);
        } else {
            tmpltZoneVO.setRemoved(null);
            tmpltZoneVO.setLastUpdated(new Date());
            _templateZoneDao.update(tmpltZoneVO.getId(), tmpltZoneVO);
        }
        txn.commit();

        return tmplt.getId();
    }

    @Override
    @DB
    public List<VMTemplateVO> listAllInZone(final long dataCenterId) {
        final SearchCriteria<VMTemplateVO> sc = TmpltsInZoneSearch.create();
        sc.setParameters("avoidtype", TemplateType.PERHOST.toString());
        sc.setParameters("state", VirtualMachineTemplate.State.Active);
        sc.setJoinParameters("tmpltzone", "zoneId", dataCenterId);
        return listBy(sc);
    }

    @Override
    public List<VMTemplateVO> listInZoneByState(final long dataCenterId, final VirtualMachineTemplate.State... states) {
        final SearchCriteria<VMTemplateVO> sc = TmpltsInZoneSearch.create();
        sc.setParameters("avoidtype", TemplateType.PERHOST.toString());
        sc.setParameters("state", (Object[]) states);
        sc.setJoinParameters("tmpltzone", "zoneId", dataCenterId);
        return listBy(sc);
    }

    @Override
    public List<VMTemplateVO> listAllActive() {
        final SearchCriteria<VMTemplateVO> sc = ActiveTmpltSearch.create();
        sc.setParameters("state", VirtualMachineTemplate.State.Active.toString());
        return listBy(sc);
    }

    @Override
    public List<VMTemplateVO> listByState(final VirtualMachineTemplate.State... states) {
        final SearchCriteria<VMTemplateVO> sc = ActiveTmpltSearch.create();
        sc.setParameters("state", (Object[]) states);
        return listBy(sc);
    }

    @Override
    public List<VMTemplateVO> listByHypervisorType(final List<HypervisorType> hyperTypes) {
        final SearchCriteria<VMTemplateVO> sc = createSearchCriteria();
        hyperTypes.add(HypervisorType.None);
        sc.addAnd("hypervisorType", SearchCriteria.Op.IN, hyperTypes.toArray());
        return listBy(sc);
    }

    @Override
    public List<VMTemplateVO> publicIsoSearch(final Boolean bootable, final boolean listRemoved, final Map<String, String> tags) {

        SearchBuilder<VMTemplateVO> sb = null;
        if (tags == null || tags.isEmpty()) {
            sb = PublicIsoSearch;
        } else {
            sb = createSearchBuilder();
            sb.and("public", sb.entity().isPublicTemplate(), SearchCriteria.Op.EQ);
            sb.and("format", sb.entity().getFormat(), SearchCriteria.Op.EQ);
            sb.and("type", sb.entity().getTemplateType(), SearchCriteria.Op.EQ);
            sb.and("bootable", sb.entity().isBootable(), SearchCriteria.Op.EQ);
            sb.and("state", sb.entity().getState(), SearchCriteria.Op.EQ);

            final SearchBuilder<ResourceTagVO> tagSearch = _tagsDao.createSearchBuilder();
            for (int count = 0; count < tags.size(); count++) {
                tagSearch.or().op("key" + String.valueOf(count), tagSearch.entity().getKey(), SearchCriteria.Op.EQ);
                tagSearch.and("value" + String.valueOf(count), tagSearch.entity().getValue(), SearchCriteria.Op.EQ);
                tagSearch.cp();
            }
            tagSearch.and("resourceType", tagSearch.entity().getResourceType(), SearchCriteria.Op.EQ);
            sb.groupBy(sb.entity().getId());
            sb.join("tagSearch", tagSearch, sb.entity().getId(), tagSearch.entity().getResourceId(), JoinBuilder.JoinType.INNER);
        }

        final SearchCriteria<VMTemplateVO> sc = sb.create();

        sc.setParameters("public", 1);
        sc.setParameters("format", "ISO");
        sc.setParameters("type", TemplateType.PERHOST.toString());
        if (bootable != null) {
            sc.setParameters("bootable", bootable);
        }

        if (!listRemoved) {
            sc.setParameters("state", VirtualMachineTemplate.State.Active);
        }

        if (tags != null && !tags.isEmpty()) {
            int count = 0;
            sc.setJoinParameters("tagSearch", "resourceType", ResourceObjectType.ISO.toString());
            for (final String key : tags.keySet()) {
                sc.setJoinParameters("tagSearch", "key" + String.valueOf(count), key);
                sc.setJoinParameters("tagSearch", "value" + String.valueOf(count), tags.get(key));
                count++;
            }
        }

        return listBy(sc);
    }

    @Override
    public List<VMTemplateVO> userIsoSearch(final boolean listRemoved) {

        SearchBuilder<VMTemplateVO> sb = null;
        sb = UserIsoSearch;
        final SearchCriteria<VMTemplateVO> sc = sb.create();

        sc.setParameters("format", ImageFormat.ISO);
        sc.setParameters("type", TemplateType.USER.toString());

        if (!listRemoved) {
            sc.setParameters("state", VirtualMachineTemplate.State.Active);
        }

        return listBy(sc);
    }

    @Override
    public VMTemplateVO findSystemVMTemplate(final long zoneId) {
        final SearchCriteria<VMTemplateVO> sc = tmpltTypeHyperSearch.create();
        sc.setParameters("templateType", Storage.TemplateType.SYSTEM);
        sc.setJoinParameters("tmplHyper", "type", Host.Type.Routing);
        sc.setJoinParameters("tmplHyper", "zoneId", zoneId);

        // order by descending order of id and select the first (this is going
        // to be the latest)
        final List<VMTemplateVO> tmplts = listBy(sc, new Filter(VMTemplateVO.class, "id", false, null, 1l));

        if (tmplts.size() > 0) {
            return tmplts.get(0);
        } else {
            return null;
        }
    }

    @Override
    public VMTemplateVO findSystemVMReadyTemplate(final long zoneId, final HypervisorType hypervisorType) {
        final SearchCriteria<VMTemplateVO> sc = readySystemTemplateSearch.create();
        sc.setParameters("templateType", Storage.TemplateType.SYSTEM);
        sc.setParameters("state", VirtualMachineTemplate.State.Active);
        sc.setJoinParameters("tmplHyper", "type", Host.Type.Routing);
        sc.setJoinParameters("tmplHyper", "zoneId", zoneId);
        sc.setJoinParameters("vmTemplateJoinTemplateStoreRef", "downloadState", VMTemplateStorageResourceAssoc.Status.DOWNLOADED);

        // order by descending order of id
        final List<VMTemplateVO> tmplts = listBy(sc, new Filter(VMTemplateVO.class, "id", false, null, null));

        if (tmplts.size() > 0) {
            if (hypervisorType == HypervisorType.Any) {
                return tmplts.get(0);
            }
            for (final VMTemplateVO tmplt : tmplts) {
                if (tmplt.getHypervisorType() == hypervisorType) {
                    return tmplt;
                }
            }
        }
        return null;
    }

    @Override
    public VMTemplateVO findRoutingTemplate(final HypervisorType hType, final String templateName) {
        SearchCriteria<VMTemplateVO> sc = tmpltTypeHyperSearch2.create();
        sc.setParameters("templateType", TemplateType.ROUTING);
        sc.setParameters("hypervisorType", hType);
        if (templateName != null) {
            sc.setParameters("templateName", templateName);
        }

        // order by descending order of id and select the first (this is going
        // to be the latest)
        List<VMTemplateVO> tmplts = listBy(sc, new Filter(VMTemplateVO.class, "id", false, null, 1l));

        if (tmplts.size() > 0) {
            return tmplts.get(0);
        } else {
            sc = tmpltTypeHyperSearch2.create();
            sc.setParameters("templateType", TemplateType.SYSTEM);
            sc.setParameters("hypervisorType", hType);
            if (templateName != null) {
                sc.setParameters("templateName", templateName);
            }

            // order by descending order of id and select the first (this is going
            // to be the latest)
            tmplts = listBy(sc, new Filter(VMTemplateVO.class, "id", false, null, 1l));

            if (tmplts.size() > 0) {
                return tmplts.get(0);
            } else {
                return null;
            }
        }
    }

    @Override
    public List<Long> listPrivateTemplatesByHost(final Long hostId) {

        final String sql =
                "select * from template_host_ref as thr INNER JOIN vm_template as t ON t.id=thr.template_id "
                        + "where thr.host_id=? and t.public=0 and t.featured=0 and t.type='USER' and t.state='Active'";

        final List<Long> l = new ArrayList<>();

        final TransactionLegacy txn = TransactionLegacy.currentTxn();

        PreparedStatement pstmt = null;
        try {
            pstmt = txn.prepareAutoCloseStatement(sql);
            pstmt.setLong(1, hostId);
            final ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                l.add(rs.getLong(1));
            }
        } catch (final SQLException e) {
            s_logger.debug("Exception: ", e);
        }
        return l;
    }

    @Override
    public Long countTemplatesForAccount(final long accountId) {
        final SearchCriteria<Long> sc = CountTemplatesByAccount.create();
        sc.setParameters("account", accountId);
        sc.setParameters("state", VirtualMachineTemplate.State.Active.toString());
        return customSearch(sc, null).get(0);
    }

    @Override
    public void loadDetails(final VMTemplateVO tmpl) {
        final Map<String, String> details = _templateDetailsDao.listDetailsKeyPairs(tmpl.getId());
        tmpl.setDetails(details);
    }

    @Override
    public void saveDetails(final VMTemplateVO tmpl) {
        final Map<String, String> detailsStr = tmpl.getDetails();
        if (detailsStr == null) {
            return;
        }
        final List<VMTemplateDetailVO> details = new ArrayList<>();
        for (final String key : detailsStr.keySet()) {
            final VMTemplateDetailVO detail = new VMTemplateDetailVO(tmpl.getId(), key, detailsStr.get(key), true);
            details.add(detail);
        }

        _templateDetailsDao.saveDetails(details);
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        final boolean result = super.configure(name, params);

        PublicSearch = createSearchBuilder();
        PublicSearch.and("public", PublicSearch.entity().isPublicTemplate(), SearchCriteria.Op.EQ);

        routerTmpltName = (String) params.get("routing.uniquename");

        s_logger.debug("Found parameter routing unique name " + routerTmpltName);
        if (routerTmpltName == null) {
            routerTmpltName = "routing";
        }

        consoleProxyTmpltName = (String) params.get("consoleproxy.uniquename");
        if (consoleProxyTmpltName == null) {
            consoleProxyTmpltName = "routing";
        }
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Use console proxy template : " + consoleProxyTmpltName);
        }

        UniqueNameSearch = createSearchBuilder();
        UniqueNameSearch.and("uniqueName", UniqueNameSearch.entity().getUniqueName(), SearchCriteria.Op.EQ);
        NameSearch = createSearchBuilder();
        NameSearch.and("name", NameSearch.entity().getName(), SearchCriteria.Op.EQ);

        NameAccountIdSearch = createSearchBuilder();
        NameAccountIdSearch.and("name", NameAccountIdSearch.entity().getName(), SearchCriteria.Op.EQ);
        NameAccountIdSearch.and("accountId", NameAccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);

        PublicIsoSearch = createSearchBuilder();
        PublicIsoSearch.and("public", PublicIsoSearch.entity().isPublicTemplate(), SearchCriteria.Op.EQ);
        PublicIsoSearch.and("format", PublicIsoSearch.entity().getFormat(), SearchCriteria.Op.EQ);
        PublicIsoSearch.and("type", PublicIsoSearch.entity().getTemplateType(), SearchCriteria.Op.EQ);
        PublicIsoSearch.and("bootable", PublicIsoSearch.entity().isBootable(), SearchCriteria.Op.EQ);
        PublicIsoSearch.and("state", PublicIsoSearch.entity().getState(), SearchCriteria.Op.EQ);

        UserIsoSearch = createSearchBuilder();
        UserIsoSearch.and("format", UserIsoSearch.entity().getFormat(), SearchCriteria.Op.EQ);
        UserIsoSearch.and("type", UserIsoSearch.entity().getTemplateType(), SearchCriteria.Op.EQ);
        UserIsoSearch.and("state", UserIsoSearch.entity().getState(), SearchCriteria.Op.EQ);

        tmpltTypeHyperSearch = createSearchBuilder();
        tmpltTypeHyperSearch.and("templateType", tmpltTypeHyperSearch.entity().getTemplateType(), SearchCriteria.Op.EQ);
        final SearchBuilder<HostVO> hostHyperSearch = _hostDao.createSearchBuilder();
        hostHyperSearch.and("type", hostHyperSearch.entity().getType(), SearchCriteria.Op.EQ);
        hostHyperSearch.and("zoneId", hostHyperSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        hostHyperSearch.groupBy(hostHyperSearch.entity().getHypervisorType());

        tmpltTypeHyperSearch.join("tmplHyper", hostHyperSearch, hostHyperSearch.entity().getHypervisorType(), tmpltTypeHyperSearch.entity().getHypervisorType(),
                JoinBuilder.JoinType.INNER);
        hostHyperSearch.done();
        tmpltTypeHyperSearch.done();

        readySystemTemplateSearch = createSearchBuilder();
        readySystemTemplateSearch.and("state", readySystemTemplateSearch.entity().getState(), SearchCriteria.Op.EQ);
        readySystemTemplateSearch.and("templateType", readySystemTemplateSearch.entity().getTemplateType(), SearchCriteria.Op.EQ);
        final SearchBuilder<TemplateDataStoreVO> templateDownloadSearch = _templateDataStoreDao.createSearchBuilder();
        templateDownloadSearch.and("downloadState", templateDownloadSearch.entity().getDownloadState(), SearchCriteria.Op.EQ);
        readySystemTemplateSearch.join("vmTemplateJoinTemplateStoreRef", templateDownloadSearch, templateDownloadSearch.entity().getTemplateId(),
                readySystemTemplateSearch.entity().getId(), JoinBuilder.JoinType.INNER);
        final SearchBuilder<HostVO> hostHyperSearch2 = _hostDao.createSearchBuilder();
        hostHyperSearch2.and("type", hostHyperSearch2.entity().getType(), SearchCriteria.Op.EQ);
        hostHyperSearch2.and("zoneId", hostHyperSearch2.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        hostHyperSearch2.and("removed", hostHyperSearch2.entity().getRemoved(), SearchCriteria.Op.NULL);
        hostHyperSearch2.groupBy(hostHyperSearch2.entity().getHypervisorType());

        readySystemTemplateSearch.join("tmplHyper", hostHyperSearch2, hostHyperSearch2.entity().getHypervisorType(), readySystemTemplateSearch.entity()
                                                                                                                                              .getHypervisorType(), JoinBuilder
                .JoinType.INNER);
        hostHyperSearch2.done();
        readySystemTemplateSearch.done();

        tmpltTypeHyperSearch2 = createSearchBuilder();
        tmpltTypeHyperSearch2.and("templateType", tmpltTypeHyperSearch2.entity().getTemplateType(), SearchCriteria.Op.EQ);
        tmpltTypeHyperSearch2.and("hypervisorType", tmpltTypeHyperSearch2.entity().getHypervisorType(), SearchCriteria.Op.EQ);
        tmpltTypeHyperSearch2.and("templateName", tmpltTypeHyperSearch2.entity().getName(), SearchCriteria.Op.EQ);

        tmpltTypeSearch = createSearchBuilder();
        tmpltTypeSearch.and("state", tmpltTypeSearch.entity().getState(), SearchCriteria.Op.EQ);
        tmpltTypeSearch.and("templateType", tmpltTypeSearch.entity().getTemplateType(), SearchCriteria.Op.EQ);

        AccountIdSearch = createSearchBuilder();
        AccountIdSearch.and("accountId", AccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        AccountIdSearch.and("publicTemplate", AccountIdSearch.entity().isPublicTemplate(), SearchCriteria.Op.EQ);
        AccountIdSearch.and("state", AccountIdSearch.entity().getState(), SearchCriteria.Op.EQ); // only list not removed templates for this account
        AccountIdSearch.done();

        final SearchBuilder<VMTemplateZoneVO> tmpltZoneSearch = _templateZoneDao.createSearchBuilder();
        tmpltZoneSearch.and("removed", tmpltZoneSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        tmpltZoneSearch.and("zoneId", tmpltZoneSearch.entity().getZoneId(), SearchCriteria.Op.EQ);

        TmpltsInZoneSearch = createSearchBuilder();
        TmpltsInZoneSearch.and("state", TmpltsInZoneSearch.entity().getState(), SearchCriteria.Op.IN);
        TmpltsInZoneSearch.and().op("avoidtype", TmpltsInZoneSearch.entity().getTemplateType(), SearchCriteria.Op.NEQ);
        TmpltsInZoneSearch.or("templateType", TmpltsInZoneSearch.entity().getTemplateType(), SearchCriteria.Op.NULL);
        TmpltsInZoneSearch.cp();
        TmpltsInZoneSearch.join("tmpltzone", tmpltZoneSearch, tmpltZoneSearch.entity().getTemplateId(), TmpltsInZoneSearch.entity().getId(), JoinBuilder.JoinType.INNER);
        tmpltZoneSearch.done();
        TmpltsInZoneSearch.done();

        ActiveTmpltSearch = createSearchBuilder();
        ActiveTmpltSearch.and("state", ActiveTmpltSearch.entity().getState(), SearchCriteria.Op.IN);

        CountTemplatesByAccount = createSearchBuilder(Long.class);
        CountTemplatesByAccount.select(null, Func.COUNT, null);
        CountTemplatesByAccount.and("account", CountTemplatesByAccount.entity().getAccountId(), SearchCriteria.Op.EQ);
        CountTemplatesByAccount.and("state", CountTemplatesByAccount.entity().getState(), SearchCriteria.Op.EQ);
        CountTemplatesByAccount.done();

        //        updateStateSearch = this.createSearchBuilder();
        //        updateStateSearch.and("id", updateStateSearch.entity().getId(), Op.EQ);
        //        updateStateSearch.and("state", updateStateSearch.entity().getState(), Op.EQ);
        //        updateStateSearch.and("updatedCount", updateStateSearch.entity().getUpdatedCount(), Op.EQ);
        //        updateStateSearch.done();

        AllFieldsSearch = createSearchBuilder();
        AllFieldsSearch.and("state", AllFieldsSearch.entity().getState(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("accountId", AllFieldsSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("id", AllFieldsSearch.entity().getId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("destroyed", AllFieldsSearch.entity().getState(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("notDestroyed", AllFieldsSearch.entity().getState(), SearchCriteria.Op.NEQ);
        AllFieldsSearch.and("updatedCount", AllFieldsSearch.entity().getUpdatedCount(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("name", AllFieldsSearch.entity().getName(), SearchCriteria.Op.EQ);
        AllFieldsSearch.done();

        return result;
    }

    @Override
    @DB
    public boolean remove(final Long id) {
        final TransactionLegacy txn = TransactionLegacy.currentTxn();
        txn.start();
        final VMTemplateVO template = createForUpdate();
        template.setRemoved(new Date());

        final VMTemplateVO vo = findById(id);
        if (vo != null) {
            if (vo.getFormat() == ImageFormat.ISO) {
                _tagsDao.removeByIdAndType(id, ResourceObjectType.ISO);
            } else {
                _tagsDao.removeByIdAndType(id, ResourceObjectType.Template);
            }
        }

        final boolean result = update(id, template);
        txn.commit();
        return result;
    }

    @Override
    public boolean updateState(
            final com.cloud.template.VirtualMachineTemplate.State currentState,
            final com.cloud.template.VirtualMachineTemplate.Event event,
            final com.cloud.template.VirtualMachineTemplate.State nextState,
            final VirtualMachineTemplate vo, final Object data) {

        final Long oldUpdated = vo.getUpdatedCount();
        final Date oldUpdatedTime = vo.getUpdated();

        final SearchCriteria<VMTemplateVO> sc = AllFieldsSearch.create();
        sc.setParameters("id", vo.getId());
        sc.setParameters("state", currentState);
        sc.setParameters("updatedCount", vo.getUpdatedCount());

        vo.incrUpdatedCount();

        final UpdateBuilder builder = getUpdateBuilder(vo);
        builder.set(vo, "state", nextState);
        builder.set(vo, "updated", new Date());

        final int rows = update((VMTemplateVO) vo, sc);
        if (rows == 0 && s_logger.isDebugEnabled()) {
            final VMTemplateVO dbTemplate = findByIdIncludingRemoved(vo.getId());
            if (dbTemplate != null) {
                final StringBuilder str = new StringBuilder("Unable to update ").append(vo.toString());
                str.append(": DB Data={id=")
                   .append(dbTemplate.getId())
                   .append("; state=")
                   .append(dbTemplate.getState())
                   .append("; updatecount=")
                   .append(dbTemplate.getUpdatedCount())
                   .append(";updatedTime=")
                   .append(dbTemplate.getUpdated());
                str.append(": New Data={id=")
                   .append(vo.getId())
                   .append("; state=")
                   .append(nextState)
                   .append("; event=")
                   .append(event)
                   .append("; updatecount=")
                   .append(vo.getUpdatedCount())
                   .append("; updatedTime=")
                   .append(vo.getUpdated());
                str.append(": stale Data={id=")
                   .append(vo.getId())
                   .append("; state=")
                   .append(currentState)
                   .append("; event=")
                   .append(event)
                   .append("; updatecount=")
                   .append(oldUpdated)
                   .append("; updatedTime=")
                   .append(oldUpdatedTime);
            } else {
                s_logger.debug("Unable to update template: id=" + vo.getId() + ", as no such template exists in the database anymore");
            }
        }
        return rows > 0;
    }
}
