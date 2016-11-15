/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 robert.gruendler@dubture.com
 *               2016 Maxim Biro <nurupo.contributions@gmail.com>
 *               2016 Isaac Aymerich <isaac.aymerich@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.segator.jenkins.scaleway;

import com.github.segator.scaleway.api.ScalewayClient;
import com.github.segator.scaleway.api.ScalewayFactory;
import com.github.segator.scaleway.api.constants.ScalewayComputeRegion;
import com.github.segator.scaleway.api.entity.ScalewayServer;
import com.github.segator.scaleway.api.entity.exceptions.ScalewayException;
import hudson.slaves.AbstractCloudComputer;

import java.util.logging.Logger;

/**
 *
 * A {@link hudson.model.Computer} implementation for Scaleway. Holds a handle
 * to an {@link Slave}.
 *
 * <p>
 * Mainly responsible for updating the Server information via
 * {@link Computer#updateInstanceDescription()}
 *
 * @author robert.gruendler@dubture.com
 * @author isaac.aymerich@gmail.com
 */
public class Computer extends AbstractCloudComputer<Slave> {

    private static final Logger LOGGER = Logger.getLogger(Computer.class.getName());

    private final String authToken;
    private final String orgToken;
    private final ScalewayComputeRegion regionId;

    private String serverId;

    public Computer(Slave slave) {
        super(slave);
        serverId = slave.getServerId();
        authToken = slave.getCloud().getAuthToken();
        orgToken = slave.getCloud().getOrgToken();
        regionId = slave.getCloud().getScalewayClient().getRegion();
    }

    public ScalewayServer updateInstanceDescription() throws ScalewayException {
        ScalewayClient scaleway = ScalewayFactory.getScalewayClient(authToken, orgToken,regionId);
        return scaleway.getServer(serverId);
    }

    @Override
    protected void onRemoved() {
        super.onRemoved();

        LOGGER.info("Slave removed, deleting server " + serverId);
        Scaleway.tryDestroyServerAsync(authToken, orgToken,regionId, serverId);
    }

    public ScalewayCloud getCloud() {
        Slave node = getNode();
        if (node != null) {
            return node.getCloud();
        }
        return null;
    }

    public int getSshPort() {
        Slave node = getNode();
        if (node != null) {
            return node.getSshPort();
        }
        return 22;
    }

    public String getRemoteAdmin() {
        Slave node = getNode();
        if (node != null) {
            return node.getRemoteAdmin();
        }
        return null;
    }

    public long getStartTimeMillis() {
        Slave node = getNode();
        if (node != null) {
            return node.getStartTimeMillis();
        }
        return 0;
    }
}
