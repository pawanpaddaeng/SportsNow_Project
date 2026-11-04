import { areFriends } from '../../social/db/friends';
import { type GameChangeProblem, getGameById, getGamePlayers } from './games';

/*  why the organizer can't invite these users: the game can't be changed (it's gone,
    someone else's, cancelled or started), it's full, or one of them isn't their friend
    or is already playing
 */
export type InviteProblem = GameChangeProblem | 'game_full' | 'not_friend' | 'already_player';

export type InviteCheckResult =
  // title so the invited friends can be told which game
  | { ok: true; title: string }
  | { ok: false; problem: InviteProblem };

/*  checks the organizer can invite friendIds to their game. Nothing is stored: an invite
    is only a notification, the friend then sends a join request like anyone else
 */
export async function checkInvites(gameId: number, organizerId: number, friendIds: number[]): Promise<InviteCheckResult> {
  const game = await getGameById(gameId);
  if (!game) return { ok: false, problem: 'not_found' };
  if (game.organizerId !== organizerId) return { ok: false, problem: 'not_organizer' };
  if (game.status === 'cancelled') return { ok: false, problem: 'cancelled' };
  // a completed game has started too
  if (game.hasStarted) return { ok: false, problem: 'started' };
  if (game.playerCount >= game.maxPlayers) return { ok: false, problem: 'game_full' };

  const friendships = await Promise.all(friendIds.map((friendId) => areFriends(organizerId, friendId)));
  if (friendships.includes(false)) return { ok: false, problem: 'not_friend' };

  const players = await getGamePlayers(gameId);
  if (players.some((player) => friendIds.includes(player.userId))) return { ok: false, problem: 'already_player' };

  return { ok: true, title: game.title };
}