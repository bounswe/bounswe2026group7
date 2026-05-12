import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Image, TouchableOpacity } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import * as FileSystem from 'expo-file-system/legacy';

type Props = {
  downloadUrl: string;
  filename: string;
  style?: object;
  onPress?: () => void;
};

export default function AuthImage({ downloadUrl, filename, style, onPress }: Props) {
  const [localUri, setLocalUri] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const token = await SecureStore.getItemAsync('userToken');
        if (!token || cancelled) return;
        const targetPath = `${FileSystem.cacheDirectory}${filename}`;
        const info = await FileSystem.getInfoAsync(targetPath);
        if (info.exists) {
          if (!cancelled) setLocalUri(targetPath);
          return;
        }
        const result = await FileSystem.downloadAsync(downloadUrl, targetPath, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!cancelled) setLocalUri(result.uri);
      } catch {
        // silently ignore
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [downloadUrl, filename]);

  if (loading && !localUri) {
    return <ActivityIndicator color="#456B50" style={{ margin: 8 }} />;
  }
  if (!localUri) return null;

  const img = (
    <Image
      source={{ uri: localUri }}
      style={[{ width: '100%', height: 200, borderRadius: 12, marginTop: 10 }, style]}
      resizeMode="cover"
    />
  );

  if (onPress) {
    return (
      <TouchableOpacity onPress={onPress} activeOpacity={0.85}>
        {img}
      </TouchableOpacity>
    );
  }
  return img;
}
