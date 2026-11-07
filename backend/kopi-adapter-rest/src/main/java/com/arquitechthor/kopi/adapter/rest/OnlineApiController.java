package com.arquitechthor.kopi.adapter.rest;

import static com.arquitechthor.kopi.adapter.rest.RestAdapterConfiguration.API;

import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.application.online.LobbyView;
import com.arquitechthor.kopi.application.online.OnlineUseCase;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/// `/api/v1/online`: playing online against a friend (plan of player versus
/// player, phase D), the same as the online page. Moves and resignations go
/// through `/api/v1/games/{id}/moves` and `…/resign`, as for any game.
@RestController
class OnlineApiController {

    private final OnlineUseCase online;

    OnlineApiController(OnlineUseCase online) {
        this.online = online;
    }

    /// The account, the invitations and the online games, after syncing.
    @GetMapping(API + "/online")
    LobbyResponse lobby() {
        return LobbyResponse.of(online.lobby());
    }

    /// `{"kopiId": "luis", "color": "WHITE" | "BLACK" | null}` (mine;
    /// `null` for random). `202 Accepted`: the game starts when the friend
    /// accepts.
    @PostMapping(API + "/online/invitations")
    ResponseEntity<Void> invite(@RequestBody InviteRequest request) {
        if (request == null || request.kopiId() == null) {
            throw new InvalidRequestException("kopiId is required");
        }
        online.invite(request.kopiId(), Optional.ofNullable(request.color()));
        return ResponseEntity.accepted().build();
    }

    /// `accept` answers the new game's id; `decline`, `cancel` (an
    /// invitation of mine) and `dismiss` (a declined one) answer `204`.
    @PostMapping(API + "/online/invitations/{relayGameId}/{action}")
    ResponseEntity<?> answer(@PathVariable String relayGameId, @PathVariable String action) {
        switch (action) {
            case "accept" -> {
                return ResponseEntity.ok(new AcceptedResponse(online.accept(relayGameId).value()));
            }
            case "decline" -> online.decline(relayGameId);
            case "cancel" -> online.cancel(relayGameId);
            case "dismiss" -> online.dismiss(relayGameId);
            default -> throw new InvalidRequestException("unknown action " + action);
        }
        return ResponseEntity.noContent().build();
    }

    /// Draw offers in an online game: `draw-offer`, `draw-accept`,
    /// `draw-decline`.
    @PostMapping(API + "/games/{id}/{action:draw-offer|draw-accept|draw-decline}")
    ApiModel.GameResponse draw(@PathVariable UUID id, @PathVariable String action) {
        var gameId = new GameId(id);
        GameView view = switch (action) {
            case "draw-offer" -> online.offerDraw(gameId);
            case "draw-accept" -> online.acceptDraw(gameId);
            default -> online.declineDraw(gameId);
        };
        return ApiModel.GameResponse.of(view);
    }

    record InviteRequest(String kopiId, Color color) {}

    record AcceptedResponse(UUID gameId) {}

    /// `GET /online`.
    ///
    /// @param kopiId  the account's Kopi id; `null` until it has one
    /// @param problem why the relay could not be asked (`UNREACHABLE`…)
    record LobbyResponse(boolean signedIn, String kopiId, boolean pendingApproval, String problem,
                         List<InvitationResponse> received, List<InvitationResponse> sent,
                         List<InvitationResponse> declined, List<OnlineGameResponse> games) {

        static LobbyResponse of(LobbyView lobby) {
            var account = lobby.account();
            return new LobbyResponse(account.signedIn(), account.kopiId().orElse(null), account.pendingApproval(),
                    lobby.problem().map(Enum::name).orElse(null), invitations(lobby.received()),
                    invitations(lobby.sent()), invitations(lobby.declined()),
                    lobby.games().stream().map(game -> new OnlineGameResponse(game.gameId().value(),
                            game.opponentKopiId(), game.myColor(), game.plies(), game.myTurn(), game.desynced()))
                            .toList());
        }

        private static List<InvitationResponse> invitations(List<LobbyView.Invitation> invitations) {
            return invitations.stream().map(i -> new InvitationResponse(i.relayGameId(), i.opponentKopiId(),
                    i.myColor(), i.createdAt())).toList();
        }
    }

    record InvitationResponse(String relayGameId, String opponentKopiId, Color myColor, Instant createdAt) {}

    record OnlineGameResponse(UUID gameId, String opponentKopiId, Color myColor, int plies, boolean myTurn,
                              boolean desynced) {}
}
