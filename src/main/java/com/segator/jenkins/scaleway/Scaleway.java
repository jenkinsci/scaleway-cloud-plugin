/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Rory Hunter (rory.hunter@blackpepper.co.uk)
 *               2016 Maxim Biro <nurupo.contributions@gmail.com>\n 2016 Isaac Aymerich <isaac.aymerich@gmail.com>
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
import com.segator.scaleway.api.entity.ScalewayServerAction;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Various utility methods that make it easier to obtain full lists of properties from Scaleway. Some API
 * calls require page number, since the results are paginated, so these utilities will exhaust all the pages and
 * return a single result set.
 *
 * @author Rory Hunter (rory.hunter@blackpepper.co.uk)
 * @author isaac.aymerich@gmail.com
 */
public final class Scaleway {

    private Scaleway() {
        throw new AssertionError();
    }

    private static final Logger LOGGER = Logger.getLogger(Scaleway.class.getName());



    private static class DestroyInfo {
        public final String authToken;
        public final String orgToken;
        public final String serverId;

        public DestroyInfo(String authToken,String orgToken, String serverId) {
            this.authToken = authToken;
            this.orgToken = orgToken;
            this.serverId = serverId;
        }
    }

    private static final List<DestroyInfo> toBeDestroyedServers = new ArrayList<DestroyInfo>();

    // sometimes servers have pending events during which you can't destroy them.
    // one of such events in spinning up a new Server. so we continiously try to
    // destroy servers in a separate thread
    private static final Thread serverDestroyer = new Thread(new Runnable() {
        @Override
        public void run() {

            do {
                String previousAuthToken = null;
                ScalewayClient client = null;
                List<ScalewayServer> servers = null;
                boolean failedToDestroy = false;

                synchronized (toBeDestroyedServers) {
                    Iterator<DestroyInfo> it = toBeDestroyedServers.iterator();
                    while (it.hasNext()) {
                        DestroyInfo di = it.next();

                        if (di.authToken != previousAuthToken) {
                            previousAuthToken = di.authToken;
                            client = ScalewayFactory.getScalewayClient(di.authToken,di.orgToken);
                            // new auth token -- new list of servers
                            servers = null;
                        }

                        try {
                            LOGGER.info("Trying to destroy server " + di.serverId);
                            client.executeServerAction(di.serverId,ScalewayServerAction.TERMINATE);
                            LOGGER.info("Server " + di.serverId + " is destroyed");
                            it.remove();
                        } catch (Exception e) {
                            // check if such server even exist in the first place
                            if (servers == null) {
                                try {
                                    servers = client.getAllServers();
                                } catch (Exception ee) {
                                    // ignore
                                }
                            }
                            if (servers != null) {
                                boolean found = false;
                                for (ScalewayServer d : servers) {
                                    if (d.getId() == di.serverId) {
                                        found = true;
                                        break;
                                    }
                                }

                                if (!found) {
                                    // such server doesn't exist, stop trying to destroy it you dummy
                                    LOGGER.info("Server " + di.serverId + " doesn't even exist, stop trying to destroy it you dummy!");
                                    it.remove();
                                    continue;
                                }
                            }
                            // such server might exist, so let's retry later
                            failedToDestroy = true;
                            LOGGER.warning("Failed to destroy server " + di.serverId);
                            LOGGER.log(Level.WARNING, e.getMessage(), e);
                        }
                    }

                    if (failedToDestroy) {
                        LOGGER.info("Retrying to destroy the servers in about 10 seconds");
                        try {
                            // sleep for 10 seconds, but wake up earlier if notified
                            toBeDestroyedServers.wait(10000);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    } else {
                        LOGGER.info("Waiting on more servers to destroy");
                        while (toBeDestroyedServers.isEmpty()) {
                            try {
                                toBeDestroyedServers.wait();
                            } catch (InterruptedException e) {
                                // ignore
                            }
                        }
                    }
                }
            } while (true);
        }
    });

    static void tryDestroyServerAsync(final String authToken,final String orgToken, final String serverId) {
        synchronized (toBeDestroyedServers) {
            LOGGER.info("Adding server to destroy " + serverId);

            toBeDestroyedServers.add(new DestroyInfo(authToken,orgToken, serverId));

            // sort by authToken
            Collections.sort(toBeDestroyedServers, new Comparator<DestroyInfo>() {
                @Override
                public int compare(DestroyInfo o1, DestroyInfo o2) {
                    return o1.authToken.compareTo(o2.authToken);
                }
            });

            toBeDestroyedServers.notifyAll();

            if (!serverDestroyer.isAlive()) {
                serverDestroyer.start();
            }
        }
    }

    private static Comparator<String> ignoringCase() {
        return new Comparator<String>() {
            @Override
            public int compare(final String o1, final String o2) {
                return o1.compareToIgnoreCase(o2);
            }
        };
    }
}
