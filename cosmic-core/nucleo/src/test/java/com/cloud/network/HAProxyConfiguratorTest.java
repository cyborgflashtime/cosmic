package com.cloud.network;

import static org.junit.Assert.assertTrue;

import com.cloud.legacymodel.communication.command.LoadBalancerConfigCommand;
import com.cloud.legacymodel.network.LoadBalancingRule.LbDestination;
import com.cloud.legacymodel.to.LoadBalancerTO;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class HAProxyConfiguratorTest {

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test method for {@link com.cloud.network.HAProxyConfigurator#generateConfiguration(LoadBalancerConfigCommand)}.
     */
    @Test
    public void testGenerateConfigurationLoadBalancerConfigCommand() {
        final LoadBalancerTO lb = new LoadBalancerTO("1", "10.2.0.1", 80, "http", "bla", false, false, false, null, 60000, 60000);
        final LoadBalancerTO[] lba = new LoadBalancerTO[1];
        lba[0] = lb;
        final HAProxyConfigurator hpg = new HAProxyConfigurator();
        LoadBalancerConfigCommand cmd = new LoadBalancerConfigCommand(lba, "10.0.0.1", "10.1.0.1", "10.1.1.1", null, 1L, "12", false);
        String result = genConfig(hpg, cmd);
        assertTrue("keepalive disabled should result in 'mode http' in the resulting haproxy config", result.contains("mode http"));

        cmd = new LoadBalancerConfigCommand(lba, "10.0.0.1", "10.1.0.1", "10.1.1.1", null, 1L, "4", true);
        result = genConfig(hpg, cmd);
        assertTrue("keepalive enabled should not result in 'mode http' in the resulting haproxy config", !result.contains("mode http"));
        // TODO
        // create lb command
        // setup tests for
        // maxconn (test for maxpipes as well)
        // httpmode
    }

    private String genConfig(final HAProxyConfigurator hpg, final LoadBalancerConfigCommand cmd) {
        final String[] sa = hpg.generateConfiguration(cmd);
        final StringBuilder sb = new StringBuilder();
        for (final String s : sa) {
            sb.append(s).append('\n');
        }
        return sb.toString();
    }

    /**
     * Test method for {@link com.cloud.network.HAProxyConfigurator#generateConfiguration(LoadBalancerConfigCommand)}.
     */
    @Test
    public void testGenerateConfigurationLoadBalancerProxyProtocolConfigCommand() {
        final List<LbDestination> dests = new ArrayList<>();
        dests.add(new LbDestination(443, 8443, "10.1.10.2", false));
        dests.add(new LbDestination(443, 8443, "10.1.10.2", true));
        final LoadBalancerTO lb = new LoadBalancerTO("1", "10.2.0.1", 443, "tcp", "http", false, false, false, dests, 60000, 60000);
        lb.setLbProtocol("tcp-proxy");
        final LoadBalancerTO[] lba = new LoadBalancerTO[1];
        lba[0] = lb;
        final HAProxyConfigurator hpg = new HAProxyConfigurator();
        final LoadBalancerConfigCommand cmd = new LoadBalancerConfigCommand(lba, "10.0.0.1", "10.1.0.1", "10.1.1.1", null, 1L, "12", false);
        final String result = genConfig(hpg, cmd);
        assertTrue("'send-proxy' should result if protocol is 'tcp-proxy'", result.contains("send-proxy"));
    }
}
