/*
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
 */

package com.datasophon.worker.actor;

import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.handler.InstallServiceHandler;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.UntypedActor;

public class InstallServiceActor extends UntypedActor {

    private static final Logger logger = LoggerFactory.getLogger(InstallServiceActor.class);

    @Override
    public void onReceive(Object msg) throws Throwable {
        if (msg instanceof InstallServiceRoleCommand) {
            InstallServiceRoleCommand command = (InstallServiceRoleCommand) msg;
            ExecResult installResult = new ExecResult();
            InstallServiceHandler serviceHandler = new InstallServiceHandler();

            logger.info("start install package {}", command.getPackageName());
            if (command.getDecompressPackageName().contains("kerberos")) {
                ArrayList<String> commands = new ArrayList<>();
                commands.add("yum");
                commands.add("install");
                commands.add("-y");
                if (ServiceRoleType.MASTER == command.getServiceRoleType()) {
                    logger.info("start to {}", commands.toString());
                    commands.add("krb5-server");
                    commands.add("krb5-workstation");
                    commands.add("krb5-libs");
                } else {
                    logger.info("start to {}", commands.toString());
                    commands.add("krb5-workstation");
                    commands.add("krb5-libs");
                }
                ExecResult execResult = ShellUtils.execWithStatus(Constants.INSTALL_PATH, commands, 180);
                if (execResult.getExecResult()) {
                    installResult = serviceHandler.install(command.getPackageName(), command.getDecompressPackageName(),
                            command.getPackageMd5(), command.getRunAs());
                }
            } else {
                installResult = serviceHandler.install(command.getPackageName(), command.getDecompressPackageName(),
                        command.getPackageMd5(), command.getRunAs());
            }
            getSender().tell(installResult, getSelf());
            logger.info("install package {}", installResult.getExecResult() ? "success" : "failed");
        } else {
            unhandled(msg);
        }
    }
}
