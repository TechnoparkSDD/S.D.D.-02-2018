package com.colorit.backend.game.lobby;

import com.colorit.backend.entities.Id;
import com.colorit.backend.entities.db.UserEntity;
import com.colorit.backend.game.messages.handlers.LobbyOutMessageHandler;
import com.colorit.backend.game.messages.output.*;
import com.colorit.backend.game.session.GameSessionsController;
import com.colorit.backend.game.session.GameSession;
import com.colorit.backend.services.IUserService;
import com.colorit.backend.websocket.RemotePointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;

import static com.colorit.backend.game.GameConfig.FULL_PARTY;

@Service
public class LobbyController {
    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyController.class);
    @NotNull
    private final GameSessionsController gameSessionsController;
    @NotNull
    private final RemotePointService remotePointService;
    @NotNull
    private final IUserService userService;
    @NotNull
    private final LobbyOutMessageHandler lobbyOutMessageHandler;

    private final HashMap<Id<Lobby>, Lobby> lobbiesMap = new HashMap<>();
    private final Set<Id<UserEntity>> freeUsers = new HashSet<>();
    private final Set<Lobby> lobbies = new HashSet<>();

    public LobbyController(@NotNull GameSessionsController gameSessionsController,
                           @NotNull RemotePointService remotePointService,
                           @NotNull IUserService userService,
                           @NotNull LobbyOutMessageHandler lobbyOutMessageHandler) {
        this.gameSessionsController = gameSessionsController;
        this.remotePointService = remotePointService;
        this.userService = userService;
        this.lobbyOutMessageHandler = lobbyOutMessageHandler;
    }

    public Set<Lobby> getLobbies() {
        return lobbies;
    }

    public boolean isLobbyAlive(Lobby lobby) {
        for (var user : lobby.getUsers()) {
            if (!remotePointService.isConnected(user)) {
                removeUser(lobby.getId(), user);
            }
        }
        return lobby.isAlive();
    }

    private boolean insureCandidate(@NotNull Id<UserEntity> candidate) {
        return remotePointService.isConnected(candidate)
                && userService.getUserEntity(candidate.getAdditionalInfo()) != null;
    }

    public void showLobbies(Id<UserEntity> userId) {
        freeUsers.add(userId);
        final List<OneLobbyInfo> lobbiesList = new ArrayList<>();
        for (Id<Lobby> lId : lobbiesMap.keySet()) {
            final Lobby lobby = lobbiesMap.get(lId);
            if (lobby != null && lobby.isActive()) {
                lobbiesList.add(new OneLobbyInfo(lobby));
            }
        }
        lobbyOutMessageHandler.sendMessageToUser(new Lobbies(lobbiesList), userId);
    }

    public void init(Id<UserEntity> uId, LobbySettings lobbySettings) {
        if (checkUserInLobby(uId)) {
            return;
        }

        final GameSession gameSession = gameSessionsController.createSession(lobbySettings.getFieldSize(),
                lobbySettings.getGameTime());
        gameSessionsController.addUser(uId, gameSession);

        final Lobby lobby = new Lobby(lobbySettings, uId, gameSession);
        lobbiesMap.put(lobby.getId(), lobby);
        lobbies.add(lobby);

        trySendMessageToFreeUsers(new OneLobbyInfo(lobby));
        freeUsers.remove(uId);
    }

    private void trySendMessageToFreeUsers(LobbyOutMessage message) {
        for (var user : freeUsers) {
            lobbyOutMessageHandler.sendMessageToUser(message, user);
        }
    }

    public void removeLobby(Lobby lobby) {
        lobbiesMap.remove(lobby.getId());
        lobbies.remove(lobby);
        gameSessionsController.deleteSession(lobby.getAssociatedSession());
        trySendMessageToFreeUsers(new LobbyDeletedInfo(lobby));
        LOGGER.info("lobby deleted " + lobby.getId() + " owner " + lobby.getOwnerId().getAdditionalInfo()
                + " count users " + lobby.getAssociatedSession().getUsers().size());
    }

    public void handleDeadUser(Lobby lobby, Id<UserEntity> user) {
        final boolean isOwner = lobby.getOwnerId().equals(user);
        gameSessionsController.removeUser(user, lobby.getAssociatedSession());
        if (lobby.getUsers().size() == 0) {
            return;
            //todo set lobby dead or not need
        }

        trySendMessageToUsers(new LobbyStateMessage(lobby.getId(), user,
                LobbyStateMessage.Action.DISCONNECTED), lobby);
        if (isOwner) {
            lobby.setOwner(lobby.getUsers().get(0));
            lobbyOutMessageHandler.sendMessageToUser(new LobbyStateMessage(lobby.getId(), lobby.getOwnerId(),
                    LobbyStateMessage.Action.NEW_OWNER), lobby.getOwnerId());
        }
    }

    private void trySendMessageToUsers(LobbyOutMessage message, Lobby lobby) {
        for (var user : lobby.getUsers()) {
            if (!lobbyOutMessageHandler.sendMessageToUser(message, user)) {
                handleDeadUser(lobby, user);
            }
        }
    }

    private void trySendMessageToLobbyUser(LobbyOutMessage message, Lobby lobby, Id<UserEntity> user) {
        if (!lobbyOutMessageHandler.sendMessageToUser(message, user)) {
            handleDeadUser(lobby, user);
        }
    }

    private boolean checkUserInLobby(Id<UserEntity> userId) {
        try {
            if (gameSessionsController.getGameUserSessions().get(userId) != null) {
                remotePointService.sendMessageToUser(userId,
                        new LobbyError("You cant create while you already play"));
                return true;
            }
            return false;
        } catch (IOException ignore) {
            return false;
        }
    }

    private boolean checkLobbyExist(Id<Lobby> lobbyId, Id<UserEntity> userId) {
        if (lobbyId == null || lobbiesMap.get(lobbyId) == null) {
            try {
                remotePointService.sendMessageToUser(userId, new LobbyError("Sorry lobby not found"));
            } catch (IOException ignore) {
                LOGGER.info("No such lobby {}", lobbyId);
            }
            return false;
        }
        return true;
    }


    public void addUser(Id<Lobby> lobbyId, Id<UserEntity> userId) {
        // error connecting to lobby
        if (!checkLobbyExist(lobbyId, userId)) {
            return;
        }

        /// user already in lobby
        if (checkUserInLobby(userId)) {
            return;
        }

        final Lobby lobby = lobbiesMap.get(lobbyId);

        if (lobby.getUsers().size() >= FULL_PARTY) {
            lobbyOutMessageHandler.sendMessageToUser(new LobbyError("You cant join to lobby, its full"), userId);
            return;
        }

        // adds user to session and removes from freeusers
        if (lobbyOutMessageHandler.sendMessageToUser(new LobbyConnected(lobby), userId)) {
            final LobbyStateMessage message = new LobbyStateMessage(lobbyId, userId,
                    LobbyStateMessage.Action.CONNECTED);
            trySendMessageToUsers(message, lobby);
            freeUsers.remove(userId);
            gameSessionsController.addUser(userId, lobby.getAssociatedSession());

            if (lobby.isReady()) {
                trySendMessageToLobbyUser(new LobbyStateMessage(lobby.getId(), null,
                        LobbyStateMessage.Action.READY), lobby, lobby.getOwnerId());
            }
        }
    }

    public void startLobby(Id<UserEntity> userId, Id<Lobby> lobbyId) {
        if (!checkLobbyExist(lobbyId, userId)) {
            return;
        }

        final Lobby lobby = lobbiesMap.get(lobbyId);
        if (!userId.equals(lobby.getOwnerId())) {
            lobbyOutMessageHandler.sendMessageToUser(new LobbyError("You cant start lobby"), userId);
            return;
        }

        if (lobby.isReady()) {
            final List<Id<UserEntity>> problemUsers = new ArrayList<>();
            for (Id<UserEntity> user : lobby.getUsers()) {
                if (!insureCandidate(user)) {
                    problemUsers.add(user);
                }
            }

            if (!problemUsers.isEmpty()) {
                lobby.getUsers().removeAll(problemUsers);
                for (var problemUser : lobby.getUsers()) {
                    trySendMessageToUsers(new LobbyStateMessage(lobbyId, problemUser,
                            LobbyStateMessage.Action.DISCONNECTED), lobby);
                }
            } else {
                lobby.getAssociatedSession().initMultiplayerSession();
                trySendMessageToUsers(new GameStart(lobby.getAssociatedSession().getPlayerIds()), lobby);
                if (lobby.isReady()) {
                    lobby.getAssociatedSession().setPlaying();
                }
            }
        }
    }

    public void reset(Lobby lobby) {

    }

    public void removeUser(Id<Lobby> lobbyId, Id<UserEntity> userId) {
        if (!checkLobbyExist(lobbyId, userId)) {
            return;
        }

        final Lobby lobby = lobbiesMap.get(lobbyId);
        if (lobby.getUsers().contains(userId)) {
            trySendMessageToUsers(new LobbyStateMessage(lobbyId, userId, LobbyStateMessage.Action.DISCONNECTED), lobby);
        }

        freeUsers.add(userId);
        gameSessionsController.removeUser(userId, lobby.getAssociatedSession());
    }
}