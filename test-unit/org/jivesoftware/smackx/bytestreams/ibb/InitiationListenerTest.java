/**
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.smackx.bytestreams.ibb;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smackx.bytestreams.BytestreamRequest;
import org.jivesoftware.smackx.bytestreams.ibb.InBandBytestreamListener;
import org.jivesoftware.smackx.bytestreams.ibb.InBandBytestreamManager;
import org.jivesoftware.smackx.bytestreams.ibb.InitiationListener;
import org.jivesoftware.smackx.bytestreams.ibb.packet.Open;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.powermock.reflect.Whitebox;

/**
 * Test for the InitiationListener class.
 * 
 * @author Henning Staib
 */
public class InitiationListenerTest {

    String initiatorJID = "initiator@xmpp-server/Smack";
    String targetJID = "target@xmpp-server/Smack";
    String sessionID = "session_id";

    Connection connection;
    InBandBytestreamManager byteStreamManager;
    InitiationListener initiationListener;
    Open initBytestream;

    /**
     * Initialize fields used in the tests.
     */
    @Before
    public void setup() {

        // mock connection
        connection = mock(Connection.class);

        // initialize InBandBytestreamManager to get the InitiationListener
        byteStreamManager = InBandBytestreamManager.getByteStreamManager(connection);

        // get the InitiationListener from InBandByteStreamManager
        initiationListener = Whitebox.getInternalState(byteStreamManager, InitiationListener.class);

        // create a In-Band Bytestream open packet
        initBytestream = new Open(sessionID, 4096);
        initBytestream.setFrom(initiatorJID);
        initBytestream.setTo(targetJID);

    }

    /**
     * If no listeners are registered for incoming In-Band Bytestream requests, all request should
     * be rejected with an error.
     * 
     * @throws Exception should not happen
     */
    @Test
    public void shouldRespondWithError() throws Exception {

        // run the listener with the initiation packet
        initiationListener.processPacket(initBytestream);

        // wait because packet is processed in an extra thread
        Thread.sleep(200);

        // capture reply to the In-Band Bytestream open request
        ArgumentCaptor<IQ> argument = ArgumentCaptor.forClass(IQ.class);
        verify(connection).sendPacket(argument.capture());

        // assert that reply is the correct error packet
        assertEquals(initiatorJID, argument.getValue().getTo());
        assertEquals(IQ.Type.ERROR, argument.getValue().getType());
        assertEquals(XMPPError.Condition.no_acceptable.toString(),
                        argument.getValue().getError().getCondition());

    }

    /**
     * Open request with a block size that exceeds the maximum block size should be replied with an
     * resource-constraint error.
     * 
     * @throws Exception should not happen
     */
    @Test
    public void shouldRejectRequestWithTooBigBlockSize() throws Exception {
        byteStreamManager.setMaximumBlockSize(1024);

        // run the listener with the initiation packet
        initiationListener.processPacket(initBytestream);

        // wait because packet is processed in an extra thread
        Thread.sleep(200);

        // capture reply to the In-Band Bytestream open request
        ArgumentCaptor<IQ> argument = ArgumentCaptor.forClass(IQ.class);
        verify(connection).sendPacket(argument.capture());

        // assert that reply is the correct error packet
        assertEquals(initiatorJID, argument.getValue().getTo());
        assertEquals(IQ.Type.ERROR, argument.getValue().getType());
        assertEquals(XMPPError.Condition.resource_constraint.toString(),
                        argument.getValue().getError().getCondition());

    }

    /**
     * If a listener for all requests is registered it should be notified on incoming requests.
     * 
     * @throws Exception should not happen
     */
    @Test
    public void shouldInvokeListenerForAllRequests() throws Exception {

        // add listener
        InBandBytestreamListener listener = mock(InBandBytestreamListener.class);
        byteStreamManager.addIncomingBytestreamListener(listener);

        // run the listener with the initiation packet
        initiationListener.processPacket(initBytestream);

        // wait because packet is processed in an extra thread
        Thread.sleep(200);

        // assert listener is called once
        ArgumentCaptor<BytestreamRequest> byteStreamRequest = ArgumentCaptor.forClass(BytestreamRequest.class);
        verify(listener).incomingBytestreamRequest(byteStreamRequest.capture());

        // assert that listener is called for the correct request
        assertEquals(initiatorJID, byteStreamRequest.getValue().getFrom());

    }

    /**
     * If a listener for a specific user in registered it should be notified on incoming requests
     * for that user.
     * 
     * @throws Exception should not happen
     */
    @Test
    public void shouldInvokeListenerForUser() throws Exception {

        // add listener
        InBandBytestreamListener listener = mock(InBandBytestreamListener.class);
        byteStreamManager.addIncomingBytestreamListener(listener, initiatorJID);

        // run the listener with the initiation packet
        initiationListener.processPacket(initBytestream);

        // wait because packet is processed in an extra thread
        Thread.sleep(200);

        // assert listener is called once
        ArgumentCaptor<BytestreamRequest> byteStreamRequest = ArgumentCaptor.forClass(BytestreamRequest.class);
        verify(listener).incomingBytestreamRequest(byteStreamRequest.capture());

        // assert that reply is the correct error packet
        assertEquals(initiatorJID, byteStreamRequest.getValue().getFrom());

    }

    /**
     * If listener for a specific user is registered it should not be notified on incoming requests
     * from other users.
     * 
     * @throws Exception should not happen
     */
    @Test
    public void shouldNotInvokeListenerForUser() throws Exception {

        // add listener for request of user "other_initiator"
        InBandBytestreamListener listener = mock(InBandBytestreamListener.class);
        byteStreamManager.addIncomingBytestreamListener(listener, "other_" + initiatorJID);

        // run the listener with the initiation packet
        initiationListener.processPacket(initBytestream);

        // wait because packet is processed in an extra thread
        Thread.sleep(200);

        // assert listener is not called
        ArgumentCaptor<BytestreamRequest> byteStreamRequest = ArgumentCaptor.forClass(BytestreamRequest.class);
        verify(listener, never()).incomingBytestreamRequest(byteStreamRequest.capture());

        // capture reply to the In-Band Bytestream open request
        ArgumentCaptor<IQ> argument = ArgumentCaptor.forClass(IQ.class);
        verify(connection).sendPacket(argument.capture());

        // assert that reply is the correct error packet
        assertEquals(initiatorJID, argument.getValue().getTo());
        assertEquals(IQ.Type.ERROR, argument.getValue().getType());
        assertEquals(XMPPError.Condition.no_acceptable.toString(),
                        argument.getValue().getError().getCondition());
    }

    /**
     * If a user specific listener and an all requests listener is registered only the user specific
     * listener should be notified.
     * 
     * @throws Exception should not happen
     */
    @Test
    public void shouldNotInvokeAllRequestsListenerIfUserListenerExists() throws Exception {

        // add listener for all request
        InBandBytestreamListener allRequestsListener = mock(InBandBytestreamListener.class);
        byteStreamManager.addIncomingBytestreamListener(allRequestsListener);

        // add listener for request of user "initiator"
        InBandBytestreamListener userRequestsListener = mock(InBandBytestreamListener.class);
        byteStreamManager.addIncomingBytestreamListener(userRequestsListener, initiatorJID);

        // run the listener with the initiation packet
        initiationListener.processPacket(initBytestream);

        // wait because packet is processed in an extra thread
        Thread.sleep(200);

        // assert user request listener is called once
        ArgumentCaptor<BytestreamRequest> byteStreamRequest = ArgumentCaptor.forClass(BytestreamRequest.class);
        verify(userRequestsListener).incomingBytestreamRequest(byteStreamRequest.capture());

        // assert all requests listener is not called
        byteStreamRequest = ArgumentCaptor.forClass(BytestreamRequest.class);
        verify(allRequestsListener, never()).incomingBytestreamRequest(byteStreamRequest.capture());

    }

    /**
     * If a user specific listener and an all requests listener is registered only the all requests
     * listener should be notified on an incoming request for another user.
     * 
     * @throws Exception should not happen
     */
    @Test
    public void shouldInvokeAllRequestsListenerIfUserListenerExists() throws Exception {

        // add listener for all request
        InBandBytestreamListener allRequestsListener = mock(InBandBytestreamListener.class);
        byteStreamManager.addIncomingBytestreamListener(allRequestsListener);

        // add listener for request of user "other_initiator"
        InBandBytestreamListener userRequestsListener = mock(InBandBytestreamListener.class);
        byteStreamManager.addIncomingBytestreamListener(userRequestsListener, "other_"
                        + initiatorJID);

        // run the listener with the initiation packet
        initiationListener.processPacket(initBytestream);

        // wait because packet is processed in an extra thread
        Thread.sleep(200);

        // assert user request listener is not called
        ArgumentCaptor<BytestreamRequest> byteStreamRequest = ArgumentCaptor.forClass(BytestreamRequest.class);
        verify(userRequestsListener, never()).incomingBytestreamRequest(byteStreamRequest.capture());

        // assert all requests listener is called
        byteStreamRequest = ArgumentCaptor.forClass(BytestreamRequest.class);
        verify(allRequestsListener).incomingBytestreamRequest(byteStreamRequest.capture());

    }

    /**
     * If a request with a specific session ID should be ignored no listeners should be notified.
     * 
     * @throws Exception should not happen
     */
    @Test
    public void shouldIgnoreInBandBytestreamRequestOnce() throws Exception {

        // add listener for all request
        InBandBytestreamListener allRequestsListener = mock(InBandBytestreamListener.class);
        byteStreamManager.addIncomingBytestreamListener(allRequestsListener);

        // add listener for request of user "initiator"
        InBandBytestreamListener userRequestsListener = mock(InBandBytestreamListener.class);
        byteStreamManager.addIncomingBytestreamListener(userRequestsListener, initiatorJID);

        // ignore session ID
        byteStreamManager.ignoreBytestreamRequestOnce(sessionID);

        // run the listener with the initiation packet
        initiationListener.processPacket(initBytestream);

        // wait because packet is processed in an extra thread
        Thread.sleep(200);

        // assert user request listener is not called
        ArgumentCaptor<BytestreamRequest> byteStreamRequest = ArgumentCaptor.forClass(BytestreamRequest.class);
        verify(userRequestsListener, never()).incomingBytestreamRequest(byteStreamRequest.capture());

        // assert all requests listener is not called
        byteStreamRequest = ArgumentCaptor.forClass(BytestreamRequest.class);
        verify(allRequestsListener, never()).incomingBytestreamRequest(byteStreamRequest.capture());

        // run the listener with the initiation packet again
        initiationListener.processPacket(initBytestream);

        // wait because packet is processed in an extra thread
        Thread.sleep(200);

        // assert user request listener is called on the second request with the
        // same session ID
        verify(userRequestsListener).incomingBytestreamRequest(byteStreamRequest.capture());

        // assert all requests listener is not called
        byteStreamRequest = ArgumentCaptor.forClass(BytestreamRequest.class);
        verify(allRequestsListener, never()).incomingBytestreamRequest(byteStreamRequest.capture());

    }

}
