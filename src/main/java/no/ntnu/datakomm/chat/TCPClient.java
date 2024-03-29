package no.ntnu.datakomm.chat;

import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.List;

public class TCPClient {
    private PrintWriter toServer;
    private BufferedReader fromServer;
    private Socket connection;

    // Hint: if you want to store a message for the last error, store it here
    private String lastError = null;

    private final List<ChatListener> listeners = new LinkedList<>();

    /**
     * Connect to a chat server. And set uo input/output streams
     *
     * @param host host name or IP address of the chat server
     * @param port TCP port of the chat server
     * @return True on success, false otherwise
     */
    public boolean connect(String host, int port) {
        boolean isConnected = false;
        try {
            this.connection = new Socket(host, port);
            this.fromServer = new BufferedReader(new InputStreamReader(this.connection.getInputStream()));
            this.toServer = new PrintWriter(this.connection.getOutputStream(), true);
            isConnected = this.connection.isConnected();
        } catch (IOException e) {
            System.err.println("Could not connect to server socket" + e.getMessage());
        }

        return isConnected;
        // Hint: Remember to process all exceptions and return false on error
        // Hint: Remember to set up all the necessary input/output stream variables
    }

    /**
     * Close the socket. This method must be synchronized, because several
     * threads may try to call it. For example: When "Disconnect" button is
     * pressed in the GUI thread, the connection will get closed. Meanwhile, the
     * background thread trying to read server's response will get error in the
     * input stream and may try to call this method when the socket is already
     * in the process of being closed. with "synchronized" keyword we make sure
     * that no two threads call this method in parallel.
     */
    public synchronized void disconnect() {
        // Hint: remember to check if connection is active
        if (this.isConnectionActive()) {
            try {
                this.toServer.close();
                this.fromServer.close();
                this.connection.close();
                this.connection = null;
                this.onDisconnect();
            } catch (IOException e) {
                System.err.println("Something went wrong on disconnect " + e.getMessage());
            }
        }
    }

    /**
     * @return true if the connection is active (opened), false if not.
     */
    public boolean isConnectionActive() {
        return this.connection != null;
    }

    /**
     * Send a command to server.
     *
     * @param cmd A command. It should include the command word and optional attributes, according to the protocol.
     * @return true on success, false otherwise
     */
    private boolean sendCommand(String cmd) {

        if (this.isConnectionActive()) {
            this.toServer.println(cmd);
            return true;
        }
        // Hint: Remember to check if connection is active
        return false;
    }

    /**
     * Send a public message to all the recipients.
     *
     * @param message Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPublicMessage(String message) {
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.
        return this.sendCommand("msg " + message);
    }

    /**
     * Send a login request to the chat server.
     *
     * @param username Username to use
     */
    public void tryLogin(String username) {
        // Hint: Reuse sendCommand() method
        String command = "login " + username;

        this.sendCommand(command);
    }

    /**
     * Send a request for latest user list to the server. To get the new users,
     * clear your current user list and use events in the listener.
     */
    public void refreshUserList() {
        this.sendCommand("users");
        // Step 5: implement this method
        // Hint: Use Wireshark and the provided chat client reference app to find out what commands the
        // client and server exchange for user listing.
    }

    /**
     * Send a private message to a single recipient.
     *
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPrivateMessage(String recipient, String message) {
        // Step 6: Implement this method
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.
        String command = "privmsg " + recipient + " " + message;
        return this.sendCommand(command);
    }


    /**
     * Send a request for the list of commands that server supports.
     */
    public void askSupportedCommands() {
        // Step 8: Implement this method
        // Hint: Reuse sendCommand() method
        this.sendCommand("help");
    }


    /**
     * Wait for chat server's response
     *
     * @return one line of text (one command) received from the server
     */
    private String waitServerResponse() {
        String oneResponseLine = "";
        try {
            oneResponseLine = this.fromServer.readLine();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            // Step 4: If you get I/O Exception or null from the stream, it means that something has gone wrong
            // with the stream and hence the socket. Probably a good idea to close the socket in that case.
            this.disconnect();
        }
        return oneResponseLine;
    }

    /**
     * Get the last error message
     *
     * @return Error message or "" if there has been no error
     */
    public String getLastError() {
        if (lastError != null) {
            return lastError;
        } else {
            return "";
        }
    }

    /**
     * Start listening for incoming commands from the server in a new CPU thread.
     */
    public void startListenThread() {
        // Call parseIncomingCommands() in the new thread.
        Thread t = new Thread(() -> {
            parseIncomingCommands();
        });
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands() {
        // Step 3: Implement this method
        // Hint: Reuse waitServerResponse() method
        // Hint: Have a switch-case (or other way) to check what type of response is received from the server
        // and act on it.
        // Hint: In Step 3 you need to handle only login-related responses.
        // Hint: In Step 3 reuse onLoginResult() method
        while (isConnectionActive()) {
            final int COMMAND_WORD_INDEX = 1;
            final int COMMAND_BODY = 2;
            try {
                String response = this.waitServerResponse();
                StringSplitter stringSplitter = new StringSplitter("\\s"); // splitRegex = " " (one space)
                stringSplitter.split(response, 2);
                String commandWord = stringSplitter.getPart(COMMAND_WORD_INDEX);        // getting first word in response which is the command word
                switch (commandWord) {
                    case "loginok":
                        this.onLoginResult(true, response);
                        break;
                    case "loginerr":
                        this.onLoginResult(false, stringSplitter.getPart(COMMAND_BODY));
                        break;
                    case "users":
                        // Step 5: update this method, handle user-list response from the server
                        // Hint: In Step 5 reuse onUserList() method
                        String[] users = stringSplitter.getAllPartsFromString(stringSplitter.getPart(COMMAND_BODY));
                        this.onUsersList(users);
                        break;
                    case "msg":
                        stringSplitter.split(stringSplitter.getPart(COMMAND_BODY), 2);
                        this.onMsgReceived(false, stringSplitter.getPart(COMMAND_WORD_INDEX), stringSplitter.getPart(COMMAND_BODY));
                        break;
                    case "privmsg":
                        // Step 7: add support for incoming chat messages from other users (types: msg, privmsg)
                        stringSplitter.split(stringSplitter.getPart(COMMAND_BODY), 2);
                        this.onMsgReceived(true, stringSplitter.getPart(COMMAND_WORD_INDEX), stringSplitter.getPart(COMMAND_BODY));
                        break;
                    case "msgerr":
                        // Step 7: add support for incoming message errors (type: msgerr)
                        this.onMsgError(stringSplitter.getPart(COMMAND_BODY));
                        break;
                    case "cmderr":
                        //  Step 7: add support for incoming command errors (type: cmderr)
                        // Hint for Step 7: call corresponding onXXX() methods which will notify all the listeners
                        this.onCmdError(stringSplitter.getPart(COMMAND_BODY));
                        break;
                    case "supported":
                        // Step 8: add support for incoming supported command list (type: supported)
                        this.onSupported(stringSplitter.getSplittedString());
                        break;
                    default:
                        System.out.println("Default triggered. Response: " + response);
                }
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            } catch (IndexOutOfBoundsException e) {
                System.out.println(e.getMessage());
            } catch (Exception e) {
                System.out.println("Horrible error...");
            }


        }
    }

    /**
     * Register a new listener for events (login result, incoming message, etc)
     *
     * @param listener
     */
    public void addListener(ChatListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     *
     * @param listener
     */
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The following methods are all event-notificators - notify all the listeners about a specific event.
    // By "event" here we mean "information received from the chat server".
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Notify listeners that login operation is complete (either with success or
     * failure)
     *
     * @param success When true, login successful. When false, it failed
     * @param errMsg  Error message if any
     */
    private void onLoginResult(boolean success, String errMsg) {
        for (ChatListener chatListener : listeners) {
            chatListener.onLoginResult(success, errMsg);
        }
        this.setLastError(errMsg);
    }

    /**
     * Notify listeners that socket was closed by the remote end (server or
     * Internet error)
     */
    private void onDisconnect() {
        //  Step 4: Implement this method
        // Hint: all the onXXX() methods will be similar to onLoginResult()
        for (ChatListener chatListener : listeners) {
            chatListener.onDisconnect();
        }
    }

    /**
     * Notify listeners that server sent us a list of currently connected users
     *
     * @param users List with usernames
     */
    private void onUsersList(String[] users) {
        // Step 5: Implement this method
        for (ChatListener chatListener : listeners) {
            chatListener.onUserList(users);
        }
    }

    /**
     * Notify listeners that a message is received from the server
     *
     * @param priv   When true, this is a private message
     * @param sender Username of the sender
     * @param text   Message text
     */
    private void onMsgReceived(boolean priv, String sender, String text) {
        // Step 7: Implement this method
        TextMessage textMessage = new TextMessage(sender, priv, text);
        for (ChatListener chatListener : listeners) {
            chatListener.onMessageReceived(textMessage);
        }
    }

    /**
     * Notify listeners that our message was not delivered
     *
     * @param errMsg Error description returned by the server
     */
    private void onMsgError(String errMsg) {
        // Step 7: Implement this method

        for (ChatListener chatListener : listeners) {
            chatListener.onMessageError(errMsg);
        }
        this.setLastError(errMsg);
    }

    /**
     * Notify listeners that command was not understood by the server.
     *
     * @param errMsg Error message
     */
    private void onCmdError(String errMsg) {
        //  Step 7: Implement this method
        for (ChatListener chatListener : listeners) {
            chatListener.onCommandError(errMsg);
        }
        this.setLastError(errMsg);
    }

    /**
     * Notify listeners that a help response (supported commands) was received
     * from the server
     *
     * @param commands Commands supported by the server
     */
    private void onSupported(String[] commands) {
        //  Step 8: Implement this method
        for (ChatListener chatListener : listeners) {
            chatListener.onSupportedCommands(commands);
        }
    }

    /**
     * Sets the last error message
     */
    private void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
