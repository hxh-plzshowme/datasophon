/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.datasophon.api.service.impl;

import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.master.PrometheusActor;
import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterHostService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.common.Constants;
import com.datasophon.common.command.GeneratePrometheusConfigCommand;
import com.datasophon.common.model.alert.AlertLabels;
import com.datasophon.common.model.alert.AlertMessage;
import com.datasophon.common.model.alert.Alerts;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterAlertHistory;
import com.datasophon.dao.entity.ClusterHostEntity;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.AlertLevel;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.dao.enums.ServiceState;
import com.datasophon.dao.mapper.ClusterAlertHistoryMapper;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import akka.actor.ActorRef;

@Service("clusterAlertHistoryService")
public class ClusterAlertHistoryServiceImpl
        extends
            ServiceImpl<ClusterAlertHistoryMapper, ClusterAlertHistory>
        implements
            ClusterAlertHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ClusterAlertHistoryServiceImpl.class);

    @Autowired
    private ClusterServiceRoleInstanceService roleInstanceService;

    @Autowired
    private ClusterServiceInstanceService serviceInstanceService;

    @Autowired
    private ClusterHostService hostService;

    @Autowired
    private ClusterInfoService clusterInfoService;

    @Override
    public void saveAlertHistory(String alertMessage) {
        AlertMessage alertMes = JSONObject.parseObject(alertMessage, AlertMessage.class);
        List<Alerts> alerts = alertMes.getAlerts();
        alerts.stream().forEach(alertInfo -> {
            String status = alertInfo.getStatus();
            AlertLabels labels = alertInfo.getLabels();
            int clusterId = labels.getClusterId();
            String instance = labels.getInstance();
            String hostname = instance.split(":")[0];
            // 查询告警历史
            List<ClusterAlertHistory> clusterAlertHistoryList =
                    this.list(
                            new QueryWrapper<ClusterAlertHistory>()
                                    .eq(Constants.ALERT_TARGET_NAME, labels.getAlertname())
                                    .eq(Constants.CLUSTER_ID, labels.getClusterId())
                                    .eq(Constants.HOSTNAME, hostname)
                                    .eq(Constants.IS_ENABLED, 1));
            for (ClusterAlertHistory clusterAlertHistory : clusterAlertHistoryList) {
                String serviceRoleName = labels.getServiceRoleName();
                if ("firing".equals(status)) { // 生成告警历史
                    // 查询服务实例，服务角色实例
                    if ("node".equals(serviceRoleName)) {
                        ClusterHostEntity clusterHost =
                                hostService.getClusterHostByHostname(hostname);
                        if (Objects.isNull(clusterAlertHistory)) {
                            clusterAlertHistory = new ClusterAlertHistory();
                            clusterAlertHistory.setClusterId(clusterId);
                            clusterAlertHistory.setAlertGroupName(labels.getJob());
                            clusterAlertHistory.setAlertTargetName(labels.getAlertname());
                            clusterAlertHistory.setCreateTime(new Date());
                            clusterAlertHistory.setUpdateTime(new Date());
                            if ("warning".equals(labels.getSeverity())) {
                                clusterAlertHistory.setAlertLevel(AlertLevel.WARN);
                                clusterHost.setHostState(3);
                            }
                            clusterAlertHistory.setAlertInfo(
                                    alertInfo.getAnnotations().getDescription());
                            clusterAlertHistory.setAlertAdvice(
                                    alertInfo.getAnnotations().getSummary());
                            clusterAlertHistory.setHostname(hostname);
                            clusterAlertHistory.setIsEnabled(1);

                            if ("exception".equals(labels.getSeverity())) {
                                clusterAlertHistory.setAlertLevel(AlertLevel.EXCEPTION);
                                clusterHost.setHostState(2);
                            }
                            this.save(clusterAlertHistory);
                        } else {
                            clusterHost.setHostState(3);
                            if ("exception".equals(labels.getSeverity())) {
                                clusterHost.setHostState(2);
                            }
                        }
                        hostService.updateById(clusterHost);
                    } else {
                        ClusterServiceRoleInstanceEntity roleInstance =
                                roleInstanceService.getOneServiceRole(
                                        serviceRoleName, hostname, clusterId);
                        if (Objects.nonNull(roleInstance)) {
                            ClusterServiceInstanceEntity serviceInstance =
                                    serviceInstanceService.getById(roleInstance.getServiceId());
                            if (Objects.isNull(clusterAlertHistory)) {
                                clusterAlertHistory = new ClusterAlertHistory();
                                clusterAlertHistory.setClusterId(clusterId);
                                clusterAlertHistory.setAlertGroupName(labels.getJob());
                                clusterAlertHistory.setAlertTargetName(labels.getAlertname());
                                clusterAlertHistory.setCreateTime(new Date());
                                clusterAlertHistory.setUpdateTime(new Date());
                                clusterAlertHistory.setServiceRoleInstanceId(roleInstance.getId());
                                if ("warning".equals(labels.getSeverity())) {
                                    clusterAlertHistory.setAlertLevel(AlertLevel.WARN);
                                }
                                clusterAlertHistory.setAlertInfo(
                                        alertInfo.getAnnotations().getDescription());
                                clusterAlertHistory.setAlertAdvice(
                                        alertInfo.getAnnotations().getSummary());

                                clusterAlertHistory.setHostname(hostname);
                                clusterAlertHistory.setIsEnabled(1);

                                serviceInstance.setServiceState(ServiceState.EXISTS_ALARM);
                                roleInstance.setServiceRoleState(ServiceRoleState.EXISTS_ALARM);
                                clusterAlertHistory.setServiceInstanceId(serviceInstance.getId());
                                if ("exception".equals(labels.getSeverity())) {
                                    clusterAlertHistory.setAlertLevel(AlertLevel.EXCEPTION);
                                    serviceInstance.setServiceState(ServiceState.EXISTS_EXCEPTION);
                                    // 查询服务角色实例
                                    roleInstance.setServiceRoleState(ServiceRoleState.STOP);
                                }
                                this.save(clusterAlertHistory);
                            } else {
                                serviceInstance.setServiceState(ServiceState.EXISTS_ALARM);
                                roleInstance.setServiceRoleState(ServiceRoleState.EXISTS_ALARM);
                                if ("exception".equals(labels.getSeverity())) {
                                    serviceInstance.setServiceState(ServiceState.EXISTS_EXCEPTION);
                                    // 查询服务角色实例
                                    roleInstance.setServiceRoleState(ServiceRoleState.STOP);
                                }
                            }
                            serviceInstanceService.updateById(serviceInstance);
                            roleInstanceService.updateById(roleInstance);
                        }
                    }
                }
                if ("resolved".equals(status)) {
                    if (Objects.nonNull(clusterAlertHistory)) {
                        clusterAlertHistory.setIsEnabled(2);
                        List<ClusterAlertHistory> warnAlertList =
                                this.lambdaQuery()
                                        .eq(ClusterAlertHistory::getHostname, hostname)
                                        .eq(
                                                ClusterAlertHistory::getAlertGroupName,
                                                serviceRoleName.toLowerCase())
                                        .eq(ClusterAlertHistory::getIsEnabled, 1)
                                        .eq(ClusterAlertHistory::getAlertLevel, AlertLevel.WARN)
                                        .ne(ClusterAlertHistory::getId, clusterAlertHistory.getId())
                                        .list();
                        if ("exception".equals(labels.getSeverity())) { // 异常告警处理
                            if ("node".equals(serviceRoleName)) {
                                // 置为正常
                                ClusterHostEntity clusterHost =
                                        hostService.getClusterHostByHostname(hostname);
                                clusterHost.setHostState(1);
                                if (Objects.nonNull(warnAlertList) && warnAlertList.size() > 0) {
                                    clusterHost.setHostState(3);
                                }
                                hostService.updateById(clusterHost);
                            } else {
                                // 查询服务角色实例
                                ClusterServiceRoleInstanceEntity roleInstance =
                                        roleInstanceService.getOneServiceRole(
                                                labels.getServiceRoleName(), hostname, clusterId);
                                if (roleInstance.getServiceRoleState() != ServiceRoleState.RUNNING) {
                                    roleInstance.setServiceRoleState(ServiceRoleState.RUNNING);
                                    if (Objects.nonNull(warnAlertList)
                                            && warnAlertList.size() > 0) {
                                        roleInstance.setServiceRoleState(
                                                ServiceRoleState.EXISTS_ALARM);
                                    }
                                    roleInstanceService.updateById(roleInstance);
                                }
                            }
                        } else {
                            // 警告告警处理
                            if ("node".equals(serviceRoleName)) {
                                // 置为正常
                                ClusterHostEntity clusterHost =
                                        hostService.getClusterHostByHostname(hostname);
                                clusterHost.setHostState(1);
                                if (Objects.nonNull(warnAlertList) && warnAlertList.size() > 0) {
                                    clusterHost.setHostState(3);
                                }
                                hostService.updateById(clusterHost);
                            } else {
                                // 查询服务角色实例
                                ClusterServiceRoleInstanceEntity roleInstance =
                                        roleInstanceService.getOneServiceRole(
                                                labels.getServiceRoleName(), hostname, clusterId);
                                if (roleInstance.getServiceRoleState() != ServiceRoleState.RUNNING) {
                                    roleInstance.setServiceRoleState(ServiceRoleState.RUNNING);
                                    if (Objects.nonNull(warnAlertList)
                                            && warnAlertList.size() > 0) {
                                        roleInstance.setServiceRoleState(
                                                ServiceRoleState.EXISTS_ALARM);
                                    }
                                    roleInstanceService.updateById(roleInstance);
                                }
                            }
                        }

                        this.updateById(clusterAlertHistory);
                    }
                }
            }
        });
    }

    @Override
    public Result getAlertList(Integer serviceInstanceId) {
        List<ClusterAlertHistory> list = lambdaQuery()
                .eq(serviceInstanceId != null, ClusterAlertHistory::getServiceInstanceId, serviceInstanceId)
                .eq(ClusterAlertHistory::getIsEnabled, 1)
                .list();
        return Result.success(list);
    }

    @Override
    public Result getAllAlertList(Integer clusterId, Integer page, Integer pageSize) {
        Integer offset = (page - 1) * pageSize;
        QueryWrapper<ClusterAlertHistory> wrapper = new QueryWrapper<ClusterAlertHistory>();
        wrapper.eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.IS_ENABLED, 1);
        List<ClusterAlertHistory> list = this.list(wrapper.last("limit " + offset + "," + pageSize));
        int count = this.count(wrapper);
        return Result.success(list).put(Constants.TOTAL, count);
    }

    @Override
    public void removeAlertByRoleInstanceIds(List<Integer> ids) {
        ClusterServiceRoleInstanceEntity roleInstanceEntity =
                roleInstanceService.getById(ids.get(0));
        ClusterInfoEntity clusterInfoEntity =
                clusterInfoService.getById(roleInstanceEntity.getClusterId());
        this.remove(
                new QueryWrapper<ClusterAlertHistory>()
                        .eq(Constants.IS_ENABLED, 1)
                        .in(Constants.SERVICE_ROLE_INSTANCE_ID, ids));
        // reload prometheus
        ActorUtils.getLocalActor(PrometheusActor.class, ActorUtils.getActorRefName(PrometheusActor.class))
                .tell(new GeneratePrometheusConfigCommand()
                        .setServiceInstanceId(roleInstanceEntity.getServiceId())
                        .setClusterFrame(clusterInfoEntity.getClusterFrame())
                        .setClusterId(roleInstanceEntity.getClusterId()), ActorRef.noSender());
    }
}
