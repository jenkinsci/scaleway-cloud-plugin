/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 robert.gruendler@dubture.com
 *               2016 Maxim Biro <nurupo.contributions@gmail.com>
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

package com.segator.jenkins.scaleway;

import com.segator.scaleway.api.ScalewayClient;
import com.segator.scaleway.api.ScalewayFactory;
import com.segator.scaleway.api.entity.ScalewayServer;
import com.segator.scaleway.api.entity.exceptions.ScalewayException;
import hudson.slaves.AbstractCloudComputer;

import java.util.logging.Logger;

/**
 *
 * A {@link hudson.model.Computer} implementation for Scaleway. Holds a handle to an {@link Slave}.
 *
 * <p>Mainly responsible for updating the {@link Droplet} information via {@link Computer#updateInstanceDescription()}
 *
 * @author robert.gruendler@dubture.com
 */
public class Computer extends AbstractCloudComputer<Slave> {

    private static final Logger LOGGER = Logger.getLogger(Computer.class.getName());

    private final String authToken;
    private final String orgToken;

    private String serverId;

    public Computer(Slave slave) {
        super(slave);
        serverId = slave.getServerId();
        authToken = slave.getCloud().getAuthToken();
        orgToken = slave.getCloud().getOrgToken();
    }

    public ScalewayServer updateInstanceDescription() throws ScalewayException {
        ScalewayClient scaleway = ScalewayFactory.getScalewayClient(authToken,orgToken);
        return scaleway.getServer(serverId);
    }

    @Override
    protected void onRemoved() {
        super.onRemoved();

        LOGGER.info("Slave removed, deleting server " + serverId);
        Scaleway.tryDestroyServerAsync(authToken,orgToken, serverId);
    }

    public Cloud getCloud() {
        return getNode().getCloud();
    }

    public int getSshPort() {
        return getNode().getSshPort();
    }

    public String getRemoteAdmin() {
        return getNode().getRemoteAdmin();
    }
    public long getStartTimeMillis() {
        return getNode().getStartTimeMillis();
    }
}
