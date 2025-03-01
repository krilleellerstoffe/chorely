package controller;

import model.RegisteredGroups;
import model.RegisteredUsers;

import shared.transferable.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * ServerController handles the overall logic on the server side.
 *
 * @author Angelica Asplund, Emma Svensson, Theresa Dannberg, Fredrik Jeppsson
 */
public class ServerController {
    private final RegisteredUsers registeredUsers = RegisteredUsers.getInstance();
    private final RegisteredGroups registeredGroups = RegisteredGroups.getInstance();
    private final LinkedBlockingQueue<Message> clientTaskBuffer = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<User, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    public ServerController(int port) {
        MessageHandler messageHandler = new MessageHandler();
        new ServerNetwork(this, port);
        Thread t1 = new Thread(messageHandler);
        t1.start();
    }

    /**
     * Adds an incoming message to the controller's queue of messages to handle.
     *
     * @param msg the incoming Message object.
     */
    public void handleMessage(Message msg) {
        clientTaskBuffer.add(msg);
    }

    /**
     * The method keeps track of the users that are currently online and
     * which client they are currently connected on.
     *
     * @param user   the logged in user
     * @param client the connected client
     */
    public boolean addOnlineClient(User user, ClientHandler client) {
        if(!onlineClients.containsKey(user)){
            onlineClients.put(user, client);
            return true;    //Return true if user could be added
        }else{
            return false;   //Return false when user cannot be added
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
     * Sends the groups that a user belongs to.
     *
     * @param user the user whose groups are sent.
     */
    public void sendSavedGroups(User user) {
        User userFromFile = registeredUsers.getUserFromFile(user);
        if (userFromFile != null) {
            if (userFromFile.getDbGroups() != null) {
                List<Group> groupMemberships = userFromFile.getDbGroups();
                for (Group gr : groupMemberships) {
                    ArrayList<Transferable> data = new ArrayList<>();
                    data.add(gr);
                    Message message = new Message(NetCommands.updateGroup, user, data);
                    sendReply(message);
                }
            }
        }
    }


    /**
     * Sends a reply in the form of a message to a client if the client is online,
     * an updated group for example. If the user is offline the message is added
     * to a queue that sends the message when the user is online again.
     *
     * @param reply the message object containing the reply
     * @return
     */
    public int sendReply(Message reply) {
        ClientHandler client = onlineClients.get(reply.getUser());
        if (client != null) {
            client.addToOutgoingMessages(reply);
            return 1;
        }
        return 0;
    }

    /**
     * Notifies all the members of a group when a change is made in the group.
     *
     * @param group the updated group containing the members to be notified.
     */
    public Group notifyGroupChanges(Group group) {
        ArrayList<User> members = group.getUsers();
        ArrayList<Transferable> data = new ArrayList<>();
        data.add(group);

        for (User u : members) {
            Message message = new Message(NetCommands.updateGroup, u, data);
            sendReply(message);
        }
        return group;
    }

    /**
     * Handles the incoming messages from the client
     *
     * @param msg is the incoming message object
     * @return
     */
    public NetCommands handleClientTask(Message msg) {
        NetCommands command = msg.getCommand();
        System.out.println("incoming: " + msg);
        switch (command) {
            case registerNewGroup:
                registerNewGroup(msg);
                break;
            case updateGroup:
                updateGroup(msg);
                break;
            case searchForUser:
                searchForUser(msg);
                break;
            case logout:
                logoutUser(msg);
                break;
            case deleteGroup:
                deleteGroup(msg);
                break;
            case notificationSent:         // @Author Johan, Måns
                sendNotifications(msg);
                break;
            case choreNotificationSent:     // @Author Johan
                sendChoreNotification(msg);
                break;
            case promoteUser:
                promoteUser(msg);
                break;
            default:
                break;
        }
        return command;
    }


    public void promoteUser(Message msg){
        User user = msg.getUser();
        Group group = (Group) msg.getData().get(0);

        registeredGroups.promoteUser(group, user);
    }

    /**
     * @return
     * @Author Johan
     * Used to send notifications to all users in the specified message's data when a chore is completed.
     * The method loops through the data of the msg parameter and sends a notification to each user.
     */
    public int sendChoreNotification(Message msg) {
        Message reply;
        if (msg.getData() != null) {
            ArrayList<Transferable> data = new ArrayList<>();
            data.addAll(msg.getData());
            System.out.println(data.size());
            System.out.println(data);
            for (int i = 0; i < data.size(); i++) {
                System.out.println(data.get(i) instanceof User);
                if (data.get(i) instanceof User) {
                    reply = new Message(NetCommands.choreNotificationReceived, (User) data.get(i), data);
                    sendReply(reply);
                }
            }
            return 0;
        }
        return 1;
    }

    /**
     * @param msg The message containing the data of the users to receive notifications.
     * @return
     * @Author Johan, Måns
     * Sends notifications to all users in the specified message's data.
     * The method loops through the data of the msg parameter and sends a notification to each user.
     * The NetCommands value of each notification is set to NetCommands.notificationReceived
     * and the data is set to the original data from msg.
     */
    private int sendNotifications(Message msg) {
        Message reply;
        if (msg.getData() != null) {
            ArrayList<Transferable> data = new ArrayList<>();
            data.addAll(msg.getData());
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i) instanceof User) {
                    System.out.println(data.get(i));
                    reply = new Message(NetCommands.notificationReceived, (User) data.get(i), data);
                    sendReply(reply);
                }
            }
            return 0;
        }
        return 1;
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
     * Removes group membership from the users that belong to the group at the time
     * of deletion and then deletes the group. Sends a groupDeleted message to
     * all users that belonged to the group.
     *
     * @param msg the message that contains the group that is deleted.
     */
    private void deleteGroup(Message msg) {
        List<Transferable> data = msg.getData();
        Group groupFromMessage = (Group) data.get(0);
        Group group = new Group(groupFromMessage);   // defensive copy.
        List<User> users = group.getUsers();

        registeredGroups.deleteGroup(group);

        group.deleteAllUsers();
        List<Transferable> list = new ArrayList<>();
        list.add(group);

        for (User user : users) {
            Message message = new Message(NetCommands.updateGroup, user, list);
            sendReply(message);
        }
    }

    /**
     * Handles a logout message by getting the ClientHandler that belongs to the
     * user in question, and then calling its logout method.
     *
     * @param msg the logout message from the client.
     */
    public void logoutUser(Message msg) {
        User user = msg.getUser();
        ClientHandler clientHandler = onlineClients.get(user);
        if (clientHandler != null) {
            clientHandler.logout(user);
        }
    }

    /**
     * Registers a new group to the server and updates all the members
     * of that group with the new group membership
     * @param request the Message object containing the group with all
     *                the members to be added.
     */
    public Group registerNewGroup(Message request) {
        Message reply = null;
        Group group = (Group) request.getData().get(0);
        Group registeredGroup = registeredGroups.writeGroupToFile(group);
        if (registeredGroup!=null) {
            ArrayList<User> members = group.getUsers();

            for (User u : members) {
                User basicUser = registeredUsers.getBasicUserInfo(u);
                basicUser.addGroupMembership(registeredGroup);
            }
            ArrayList<Transferable> data = new ArrayList<>();
            data.add(registeredGroup);
            reply = new Message(NetCommands.newGroupOk, request.getUser(), data);
            sendReply(reply);
            notifyGroupChanges(registeredGroup);
        } else {
            ErrorMessage errorMessage = new ErrorMessage("Registrering av grupp misslyckades.");
            reply = new Message(NetCommands.newGroupDenied, request.getUser(), errorMessage);
            sendReply(reply);
        }
        return registeredGroup;
    }
    /**
     * Updates a registered group with new change and notifies all the
     * members of that group.
     *
     * @param request The Message object containing the updated group.
     */
    public void updateGroup(Message request) {
        Group updatedGroup = (Group) request.getData().get(0);
        registeredGroups.updateGroup(updatedGroup);
        notifyGroupChanges(updatedGroup);
    }

    /**
     * Inner class MessageHandler handles the incoming messages from the client one at a time.
     */
    private class MessageHandler implements Runnable {

        @Override
        public void run() {
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
