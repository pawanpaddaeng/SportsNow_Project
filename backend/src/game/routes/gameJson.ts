import type { DiscoverGame, Game, GamePlayer } from '../db/games';
import type { PendingJoinRequest } from '../db/joinRequests';

/*  JSON shapes of games and join requests sent to the app. Every endpoint that
    returns games uses gameJson so the app has one game model
 */

export function gameJson(game: Game) {
  return {
    game_id: game.gameId,
    organizer_id: game.organizerId,
    title: game.title,
    sport: game.sport,
    description: game.description,
    starts_at: game.startsAt,
    ends_at: game.endsAt,
    location_name: game.locationName,
    address: game.address,
    latitude: game.latitude,
    longitude: game.longitude,
    max_players: game.maxPlayers,
    player_count: game.playerCount,
    skill_level: game.skillLevel,
    status: game.status,
    created_at: game.createdAt,
  };
}

export function gamePlayerJson(player: GamePlayer) {
  return {
    user_id: player.userId,
    first_name: player.firstName,
    last_name: player.lastName,
  };
}

export function discoverGameJson(entry: DiscoverGame) {
  return {
    game: gameJson(entry.game),
    distance_m: entry.distanceMeters,
    join_request_status: entry.joinRequestStatus,
  };
}

export function pendingJoinRequestJson(request: PendingJoinRequest) {
  return {
    request_id: request.requestId,
    user: { user_id: request.userId, first_name: request.firstName, last_name: request.lastName },
    skill_level: request.skillLevel,
    play_frequency: request.playFrequency,
    message: request.message,
    created_at: request.createdAt,
  };
}
