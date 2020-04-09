package shared.transferable;

public enum NetCommands implements Transferable {
    register,
    registrationOk,
    registrationDenied,
    registerNewGroup,
    newGroupOk,
    newGroupDenied,
    addUserToGroup,
    addNewChore,
    newChoreOk,
    addNewReward,
    newRewardOk,
    claimReward,
    internalClientError,
    connectionStatus,
    notConnected,
    connected,
    connectionFailed,
    requestUserGroups,
    userHasNoGroup,
    userHasGroupUpdate;
}
