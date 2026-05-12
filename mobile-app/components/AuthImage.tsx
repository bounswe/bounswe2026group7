import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Dimensions,
  Image,
  Modal,
  StyleSheet,
  TouchableOpacity,
  TouchableWithoutFeedback,
  View,
} from 'react-native';
import * as SecureStore from 'expo-secure-store';
import * as FileSystem from 'expo-file-system/legacy';
import apiClient from '../api/client';

type Props = {
  downloadUrl: string;
  filename: string;
  style?: object;
  onPress?: () => void;
};

function resolveUrl(downloadUrl: string): string {
  try {
    const clientBase = (apiClient.defaults.baseURL ?? '').replace(/\/api\/?$/, '');
    const parsed = new URL(downloadUrl);
    return clientBase + parsed.pathname + parsed.search;
  } catch {
    return downloadUrl;
  }
}

export default function AuthImage({ downloadUrl, filename, style, onPress }: Props) {
  const [localUri, setLocalUri] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [fullscreen, setFullscreen] = useState(false);

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
        const resolvedUrl = resolveUrl(downloadUrl);
        const result = await FileSystem.downloadAsync(resolvedUrl, targetPath, {
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

  const handlePress = () => {
    if (onPress) {
      onPress();
    } else {
      setFullscreen(true);
    }
  };

  return (
    <>
      <TouchableOpacity onPress={handlePress} activeOpacity={0.9}>
        <Image
          source={{ uri: localUri }}
          style={[styles.thumbnail, style]}
          resizeMode="cover"
        />
      </TouchableOpacity>

      <Modal
        visible={fullscreen}
        transparent
        animationType="fade"
        onRequestClose={() => setFullscreen(false)}
      >
        <TouchableWithoutFeedback onPress={() => setFullscreen(false)}>
          <View style={styles.overlay}>
            <Image
              source={{ uri: localUri }}
              style={styles.fullscreenImage}
              resizeMode="contain"
            />
          </View>
        </TouchableWithoutFeedback>
      </Modal>
    </>
  );
}

const { width, height } = Dimensions.get('window');

const styles = StyleSheet.create({
  thumbnail: {
    width: '100%',
    height: 200,
    borderRadius: 12,
    marginTop: 10,
  },
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.92)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  fullscreenImage: {
    width,
    height,
  },
});
