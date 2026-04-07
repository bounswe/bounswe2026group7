import { router } from 'expo-router';
import { View, Text, TouchableOpacity, StyleSheet, Image } from 'react-native';

export default function OnboardingScreen() {
  return (
    <View style={styles.container}>
      <View style={styles.topCircle} />
      <View style={styles.bottomLeftCircle} />
      <View style={styles.bottomRightCircle} />

      <View style={styles.statusRow}>
        <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
        <Text style={styles.statusIcons}>◔ ▮</Text>
      </View>

      <View style={styles.centerContent}>
        <View style={styles.iconBox}>
          <Image
            source={require('../assets/images/splash-icon.png')}
            style={styles.logoImage}
            resizeMode="contain"
          />
        </View>

        <Text style={styles.title}>
          Grow with{'\n'}
          <Text style={styles.titleItalic}>purpose.</Text>
        </Text>

        <Text style={styles.subtitle}>
          {"Connect with mentors who've walked"}{'\n'}
          your path. Build something{'\n'}
          meaningful together.
        </Text>

        <View style={styles.dotsRow}>
          <View style={[styles.dot, styles.activeDot]} />
          <View style={styles.dot} />
          <View style={styles.dot} />
        </View>
      </View>

      <View style={styles.bottomArea}>
        <TouchableOpacity
          style={styles.primaryButton}
          onPress={() => router.push('/register')}
        >
          <Text style={styles.primaryButtonText}>Get Started</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.secondaryButton}
          onPress={() => router.push('/login')}
        >
          <Text style={styles.secondaryButtonText}>I already have an account</Text>
        </TouchableOpacity>

        <Text style={styles.versionText}>MentorNet v1.0.0</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#5F8465',
    paddingHorizontal: 28,
    paddingTop: 54,
    paddingBottom: 36,
    justifyContent: 'space-between',
    overflow: 'hidden',
  },
  topCircle: {
    position: 'absolute',
    width: 340,
    height: 340,
    borderRadius: 170,
    backgroundColor: 'rgba(255,255,255,0.06)',
    top: -20,
    right: -90,
  },
  bottomLeftCircle: {
    position: 'absolute',
    width: 230,
    height: 230,
    borderRadius: 115,
    backgroundColor: 'rgba(255,255,255,0.05)',
    bottom: 120,
    left: -60,
  },
  bottomRightCircle: {
    position: 'absolute',
    width: 170,
    height: 170,
    borderRadius: 85,
    backgroundColor: 'rgba(255,255,255,0.05)',
    bottom: 210,
    right: 25,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  statusText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
  statusIcons: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
  centerContent: {
    alignItems: 'center',
    marginTop: 20,
  },
  iconBox: {
    width: 168,
    height: 168,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 46,
  },
  logoImage: {
    width: 168,
    height: 168,
  },
  title: {
    color: '#F7F4EE',
    fontSize: 34,
    lineHeight: 42,
    textAlign: 'center',
    fontWeight: '700',
    marginBottom: 18,
  },
  titleItalic: {
    fontStyle: 'italic',
    fontWeight: '700',
  },
  subtitle: {
    color: 'rgba(247,244,238,0.85)',
    fontSize: 14,
    lineHeight: 23,
    textAlign: 'center',
    marginBottom: 34,
    fontWeight: '500',
  },
  dotsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: 'rgba(255,255,255,0.35)',
  },
  activeDot: {
    width: 32,
    borderRadius: 8,
    backgroundColor: '#FFFFFF',
  },
  bottomArea: {
    marginBottom: 8,
  },
  primaryButton: {
    backgroundColor: '#F8F8F6',
    borderRadius: 22,
    paddingVertical: 20,
    alignItems: 'center',
    marginBottom: 18,
  },
  primaryButtonText: {
    color: '#5F8465',
    fontSize: 17,
    fontWeight: '700',
  },
  secondaryButton: {
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.22)',
    borderRadius: 22,
    paddingVertical: 20,
    alignItems: 'center',
    marginBottom: 28,
  },
  secondaryButtonText: {
    color: '#F3F3EF',
    fontSize: 16,
    fontWeight: '600',
  },
  versionText: {
    textAlign: 'center',
    color: 'rgba(255,255,255,0.25)',
    fontSize: 13,
    fontWeight: '600',
  },
});
