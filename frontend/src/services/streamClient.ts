import { StreamChat } from 'stream-chat';
import { config } from '../config/env';

let client: StreamChat | null = null;

export async function getStreamClient(
  userId: string,
  token: string,
): Promise<StreamChat> {
  if (client && client.userID === userId) {
    return client;
  }

  // Disconnect existing client if switching users
  if (client) {
    await client.disconnectUser();
    client = null;
  }

  client = StreamChat.getInstance(config.getstreamApiKey);
  await client.connectUser({ id: userId }, token);
  return client;
}

export function getExistingStreamClient(): StreamChat | null {
  return client;
}

export async function disconnectStream(): Promise<void> {
  if (client) {
    await client.disconnectUser();
    client = null;
  }
}
