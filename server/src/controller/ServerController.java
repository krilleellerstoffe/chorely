package controller;

import model.RegisteredGroups;
import model.RegisteredUsers;

import shared.transferable.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * ServerController handles the over all logic on the server side.
 * The class contains the main method that starts the program and makes it possible for a client
 * to obtain a connection by creating an instance of ServerNetwork.
 * version 3.0 2020-04-22
 *
 * @author Angelica Asplund, Emma Svensson and Theresa Dannberg
 */
public class ServerController implements ClientListener {
    private final static Logger messagesLogger = Logger.getLogger("messages");
    private RegisteredUsers registeredUsers;
    private RegisteredGroups registeredGroups;
    private LinkedBlockingQueue<Message> clientTaskBuffer;
    private ConcurrentHashMap<User, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    public ServerController() {
        final int PORT = 6583;

        registeredUsers = new RegisteredUsers();
        registeredGroups = new RegisteredGroups();
        clientTaskBuffer = new LinkedBlockingQueue<>();
        MessageHandler messageHandler = new MessageHandler();
        new ServerNetwork(this, PORT);
        Thread t1 = new Thread(messageHandler);

        t1.start();
    }

    /**
     * Adds an outgoing message to the buffer-queue.
     *
     * @param msg the outgoing Message object.
     */
    @Override
    public void sendMessage(Message msg) {
        clientTaskBuffer.add(msg);
    }

    /**
     * The method keeps track of the users that are currently online and
     * which client they are currently connected on.
     *
     * @param user   the logged in user
     * @param client the connected client
     */
    public void addOnlineClient(User user, ClientHandler client) {
        System.out.println("USER:" + user);
        onlineClients.put(user, client);

        User userFromFile = registeredUsers.getUserFromFile(user);
        if (userFromFile != null) {
            if (userFromFile.getGroups() != null) {
                ArrayList<GenericID> groupMemberships = userFromFile.getGroups();
                for (GenericID id : groupMemberships) {
                    Group group = registeredGroups.getGroupByID(id);
                    ArrayList<Transferable> data = new ArrayList<>();
                    data.add(group);
                    Message message = new Message(NetCommands.updateGroup, user, data);
                    sendReply(message);
                }
            }
        }
    }

    /**
     * Removes the user from the list with online clients when the log off.
     *
     * @param user the user to be removed from the list.
     */
    public void removeOnlineClient(User user) {
        onlineClients.remove(user);
    }

    /**
     * Sends a reply in the form of a message to a client if the client is online,
     * an updated group for example. If the user is offline the message is added
     * to a queue that sends the message when the user is online again.
     *
     * @param reply the message object containing the reply
     */
    public void sendReply(Message reply) {
        ClientHandler client = onlineClients.get(reply.getUser());
        if (client != null) {
            client.addToOutgoingMessages(reply);
        }
    }

    /**
     * Notifies all the members of a group when a change is made in the group.
     *
     * @param group the updated group containing the members to be notified.
     */
    public void notifyGroupChanges(Group group) {
        ArrayList<User> members = group.getUsers();
        ArrayList<Transferable> data = new ArrayList<>();
        data.add(group);

        for (User u : members) {
            Message message = new Message(NetCommands.updateGroup, u, data);
            sendReply(message);
        }
    }

    /**
     * Handles the incoming messages from the client
     *
     * @param msg is the incoming message object
     */
    public void handleClientTask(Message msg) {
        NetCommands command = msg.getCommand();

        switch (command) {
            case login:
                logIn(msg);
                break;
            case registerUser:
                registerUser(msg);
                break;
            case registerNewGroup:
                registerNewGroup(msg);
                break;
            case updateGroup:
                updateGroup(msg);
                break;
            case searchForUser:
                searchForUser(msg);
                break;
            default:
                //TODO:  kod för default case. Vad kan man skriva här?
                break;
        }
    }

    /**
     * Checks if user is already registered and ok to log in
     *
     * @param request the Message object containing the user to be checked.
     */
    public void logIn(Message request) {
        Message reply;
        User user = request.getUser();
        User userFromFile = registeredUsers.getUserFromFile(user);

        if (user.compareUsernamePassword(userFromFile)) {
            reply = new Message(NetCommands.loginOk, user);
        } else {
            ErrorMessage errorMessage = new ErrorMessage("Fel användarnamn eller lösenord, försök igen!");
            reply = new Message(NetCommands.loginDenied, user, errorMessage);
        }

        sendReply(reply);
    }

    /**
     * Adds the incoming user to RegisteredUsers
     *
     * @param request the Message object containing the user to be added.
     */
    public void registerUser(Message request) {
        Message reply;

        if (registeredUsers.userNameAvailable(request.getUser().getUsername())) {
            registeredUsers.writeUserToFile(request.getUser());
            reply = new Message(NetCommands.registrationOk, request.getUser());
            sendReply(reply);
        } else {
            ErrorMessage errorMessage = new ErrorMessage("Användarnamnet är upptaget, välj ett annat.");
            reply = new Message(NetCommands.registrationDenied, request.getUser(), errorMessage);

            sendReply(reply);
            onlineClients.get(request.getUser()).throwOut();
        }
    }

    /**
     * Registers a new group to the server and updates all the members
     * of that group with the new group membership
     *
     * @param request the Message object containing the group with all
     *                the members to be added.
     */
    public void registerNewGroup(Message request) {
        Message reply = null;
        Group group = (Group) request.getData().get(0);

        if (registeredGroups.groupIdAvailable(group.getGroupID())) {
            registeredGroups.updateGroup(group);
            ArrayList<User> members = group.getUsers();
            GenericID groupID = group.getGroupID();
            for (User u : members) {
                u.addGroupMembership(groupID);
                registeredUsers.updateUser(u);
                System.out.println(u.getUsername() + u.getGroups().get(0).getId());
            }
            reply = new Message(NetCommands.newGroupOk, request.getUser());
            sendReply(reply);
            notifyGroupChanges(group);
        } else {
            ErrorMessage errorMessage = new ErrorMessage("Registrering av grupp misslyckades.");
            reply = new Message(NetCommands.newGroupDenied, request.getUser(), errorMessage);
            sendReply(reply);
        }
    }

    /**
     * Updates a registered group with new change and notifies all the
     * members of that group.
     *
     * @param request The Message object containing the updated group.
     */
    public void updateGroup(Message request) {
        Group group = (Group) request.getData().get(0);
        registeredGroups.updateGroup(group);
        updateUsersInGroup(group);
        notifyGroupChanges(group);
    }

    /**
     * Updates the group membership of the added or removed users
     *
     * @param group is the group that contains changes in members
     */
    private void updateUsersInGroup(Group group) {
        ArrayList<User> members = group.getUsers();
        GenericID id = group.getGroupID();
        for (User u : members) {
            u.addGroupMembership(id);
            registeredUsers.updateUser(u);
        }
        //TODO: Ta bort raderade gruppmedlemmar
    }

    /**
     * Looks for a requested user among registered users.
     *
     * @param request is the message object that contains the user searched for
     */
    public void searchForUser(Message request) {
        Message reply;
        User dummyUser = (User) request.getData().get(0);

        if (registeredUsers.findUser(dummyUser) != null) {
            User foundUser = registeredUsers.findUser(dummyUser);
            List<Transferable> data = Arrays.asList(new Transferable[]{foundUser});
            reply = new Message(NetCommands.userExists, request.getUser(), data);
        } else {
            ErrorMessage errorMessage = new ErrorMessage("Användaren finns inte");
            reply = new Message(NetCommands.userDoesNotExist, request.getUser(), errorMessage);
        }
        sendReply(reply);
    }

    /**
     * Inner class MessageHandler handles the incoming messages from the client one at a time.
     */
    private class MessageHandler implements Runnable {

        @Override
        public void run() {
            ArrayList<Transferable> list;
            while (true) {
                try {
                    Message message = clientTaskBuffer.take();
                    handleClientTask(message);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
