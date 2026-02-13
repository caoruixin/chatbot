export const config = {
  getstreamApiKey: import.meta.env.VITE_GETSTREAM_API_KEY as string,
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL as string) || '',
};
