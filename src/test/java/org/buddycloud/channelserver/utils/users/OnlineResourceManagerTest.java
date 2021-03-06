package org.buddycloud.channelserver.utils.users;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.buddycloud.channelserver.Configuration;
import org.buddycloud.channelserver.channel.ChannelManager;
import org.buddycloud.channelserver.db.exception.NodeStoreException;
import org.buddycloud.channelserver.packetHandler.iq.IQTestHandler;
import org.buddycloud.channelserver.pubsub.model.NodeSubscription;
import org.buddycloud.channelserver.pubsub.model.impl.NodeSubscriptionImpl;
import org.buddycloud.channelserver.pubsub.subscription.Subscriptions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.xmpp.resultsetmanagement.ResultSetImpl;

public class OnlineResourceManagerTest extends IQTestHandler {

    private OnlineResourceManager onlineUser;
    private Properties configuration;
    private JID localUserLaptop = new JID("user@server1.com/laptop");
    private JID localUserDesktop = new JID("user@server1.com/desktop");
    private JID localUserNoResource = new JID("user@server1.com");
    private JID remoteBuddycloudServer = new JID("channels.buddycloud.org");
    private ChannelManager channelManager;

    private JID remoteUser = new JID("user@server2.com/remote");

    @Before
    public void setUp() throws Exception {
        configuration = Mockito.mock(Properties.class);
        Mockito.when(configuration.getProperty(Configuration.CONFIGURATION_SERVER_DOMAIN)).thenReturn("server1.com");

        channelManager = Mockito.mock(ChannelManager.class);

        Mockito.when(channelManager.onlineJids(Mockito.any(JID.class))).thenThrow(Exception.class);
        Mockito.doThrow(Exception.class).when(channelManager).jidOffline(Mockito.any(JID.class));
        Mockito.doThrow(Exception.class).when(channelManager).jidOnline(Mockito.any(JID.class));

        onlineUser = new OnlineResourceManager(configuration, channelManager);
    }

    @Test(expected = NullPointerException.class)
    public void testNotSettingCorrectConfigurationKeysThrowsException() {
        Properties configuration = Mockito.mock(Properties.class);
        Mockito.when(configuration.getProperty(Configuration.CONFIGURATION_SERVER_DOMAIN)).thenReturn(null);
        onlineUser = new OnlineResourceManager(configuration, channelManager);
    }

    @Test
    public void testRequestingOnlineResourcesForOfflineUserReturnsNone() throws Exception {
        ArrayList<JID> users = onlineUser.getResources(localUserNoResource);
        assertEquals(0, users.size());
    }

    @Test
    public void testCanSetAUserAsOnlineAndSeeInOnlineResourcesList() throws Exception {

        onlineUser.updateStatus(localUserDesktop, "chat");
        ArrayList<JID> users = onlineUser.getResources(localUserNoResource);
        assertEquals(1, users.size());
    }

    @Test
    public void testAttemptingToUpdateStatusOfUserNotFromDomainIsIgnored() throws Exception {

        onlineUser.updateStatus(remoteUser, "chat");
        ArrayList<JID> users = onlineUser.getResources(new JID(remoteUser.toBareJID()));
        assertEquals(0, users.size());
    }

    @Test
    public void testAttemptingToUpdateStatusOfUserFromExternallyValidatedDomain() throws Exception {
        when(configuration.getProperty(Configuration.CONFIGURATION_LOCAL_DOMAIN_CHECKER)).thenReturn(Boolean.TRUE.toString());
        onlineUser.updateStatus(remoteUser, "online");
        ArrayList<JID> users = onlineUser.getResources(new JID(remoteUser.toBareJID()));
        assertEquals(1, users.size());
    }

    @Test
    public void testCanGoOnlineOnTwoResourcesAndRetrieveBoth() throws Exception {
        onlineUser.updateStatus(localUserDesktop, "chat");
        onlineUser.updateStatus(localUserLaptop, "chat");
        ArrayList<JID> users = onlineUser.getResources(localUserNoResource);
        assertEquals(2, users.size());
        assertEquals(localUserDesktop, users.get(0));
        assertEquals(localUserLaptop, users.get(1));
    }

    @Test
    public void testCanGoOnlineOnTwoResourcesTakeOneOfflineAndRetrieveOneBack() throws Exception {
        onlineUser.updateStatus(localUserDesktop, "chat");
        onlineUser.updateStatus(localUserLaptop, "chat");
        onlineUser.updateStatus(localUserDesktop, OnlineResourceManager.UNAVAILABLE);
        ArrayList<JID> users = onlineUser.getResources(localUserNoResource);
        assertEquals(1, users.size());
        assertEquals(localUserLaptop, users.get(0));
    }

    @Test
    public void testCanConstructAJidIndependentlyAndItWillStillMatch() throws Exception {
        onlineUser.updateStatus(localUserDesktop, "chat");
        onlineUser.updateStatus(localUserLaptop, "chat");
        onlineUser.updateStatus(new JID(localUserDesktop.toFullJID()), OnlineResourceManager.UNAVAILABLE);
        ArrayList<JID> users = onlineUser.getResources(localUserNoResource);
        assertEquals(1, users.size());
        assertEquals(new JID(localUserLaptop.toFullJID()), users.get(0));
    }

    @Test
    public void testPassingAFullJidReturnsToGetResourcesOnlyReturnsThatJid() throws Exception {
        ArrayList<JID> users = onlineUser.getResources(localUserDesktop);
        assertEquals(1, users.size());
        assertEquals(localUserDesktop, users.get(0));
    }

    @Test
    public void testPassingRemoteBuddycloudServerReturnsOnlyThatJid() throws Exception {
        ArrayList<JID> users = onlineUser.getResources(remoteBuddycloudServer);
        assertEquals(1, users.size());
        assertEquals(remoteBuddycloudServer, users.get(0));
    }

    @Test
    public void testAddingTheSameResourceTwiceDoesntResultInTwoEntries() throws Exception {
        onlineUser.updateStatus(localUserDesktop, "chat");
        onlineUser.updateStatus(localUserDesktop, "chat");

        ArrayList<JID> users = onlineUser.getResources(localUserNoResource);
        assertEquals(1, users.size());
        assertEquals(new JID(localUserDesktop.toFullJID()), users.get(0));
    }

    @Test
    public void testAutoSubscribeToNoListeners() throws Exception {

        Mockito.when(channelManager.getNodeSubscriptionListeners()).thenReturn(new ResultSetImpl<NodeSubscription>(new LinkedList<NodeSubscription>()));

        BlockingQueue<Packet> outQueue = new LinkedBlockingQueue<Packet>();
        onlineUser.subscribeToNodeListeners(outQueue);

        Assert.assertTrue(outQueue.isEmpty());
    }

    @Test
    public void testAutoSubscribeToListeners() throws Exception {

        JID jid = new JID("user@server.com");
        LinkedList<NodeSubscription> subscriptions = new LinkedList<NodeSubscription>();
        subscriptions.add(new NodeSubscriptionImpl("nodeId", jid, Subscriptions.subscribed, null));
        Mockito.when(channelManager.getNodeSubscriptionListeners()).thenReturn(new ResultSetImpl<NodeSubscription>(subscriptions));

        BlockingQueue<Packet> outQueue = new LinkedBlockingQueue<Packet>();
        onlineUser.subscribeToNodeListeners(outQueue);

        Assert.assertFalse(outQueue.isEmpty());
        Presence presence = (Presence) outQueue.poll();
        Assert.assertEquals(jid, presence.getTo());
        Assert.assertEquals(Presence.Type.subscribe, presence.getType());
    }

    @Test
    public void ifUsingDatabaseWeSeeRequestForOnlineUsersMethod() throws Exception {
        Mockito.when(configuration.getProperty(Mockito.eq(Configuration.PERSIST_PRESENCE_DATA), Mockito.anyString())).thenReturn("true");

        channelManager = Mockito.mock(ChannelManager.class);
        onlineUser = new OnlineResourceManager(configuration, channelManager);
        onlineUser.getResources(localUserNoResource);
        Mockito.verify(channelManager, Mockito.times(1)).onlineJids(Mockito.eq(localUserNoResource));
    }

    @Test
    public void ifUsingDatabaseWeSeeRequestForAddingUser() throws Exception {
        Mockito.when(configuration.getProperty(Mockito.eq(Configuration.PERSIST_PRESENCE_DATA), Mockito.anyString())).thenReturn("true");

        channelManager = Mockito.mock(ChannelManager.class);
        onlineUser = new OnlineResourceManager(configuration, channelManager);
        onlineUser.updateStatus(localUserLaptop, "chat");

        Mockito.verify(channelManager, Mockito.times(1)).jidOnline(Mockito.eq(localUserLaptop));
    }

    @Test
    public void ifUsingDatabaseWeSeeRequestForRemovingUser() throws NodeStoreException {
        Mockito.when(configuration.getProperty(Mockito.eq(Configuration.PERSIST_PRESENCE_DATA), Mockito.anyString())).thenReturn("true");

        channelManager = Mockito.mock(ChannelManager.class);
        onlineUser = new OnlineResourceManager(configuration, channelManager);
        onlineUser.updateStatus(localUserLaptop, OnlineResourceManager.UNAVAILABLE);
        Mockito.verify(channelManager, Mockito.times(1)).jidOffline(Mockito.eq(localUserLaptop));
    }
}
