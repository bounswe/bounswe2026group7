import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
  Alert,
  FlatList,
} from 'react-native';
import * as Location from 'expo-location';

type LocationData = {
  city: string;
  latitude: number;
  longitude: number;
};

interface LocationPickerProps {
  initialLocation?: LocationData;
  onLocationSelect: (location: LocationData) => void;
  roleTheme?: 'mentor' | 'mentee';
}

export default function LocationPicker({
  initialLocation,
  onLocationSelect,
  roleTheme = 'mentee',
}: LocationPickerProps) {
  const [searchQuery, setSearchQuery] = useState(initialLocation?.city || '');
  const [suggestions, setSuggestions] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [isManual, setIsManual] = useState(false);

  // Coordinate rounding for privacy (2 decimal places ~1km precision)
  const roundCoord = (val: number) => Math.round(val * 100) / 100;

  // Validation
  const isValidLat = (lat: number) => lat >= -90 && lat <= 90;
  const isValidLon = (lon: number) => lon >= -180 && lon <= 180;

  const handleUseCurrentLocation = async () => {
    setLoading(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        Alert.alert(
          'Permission Denied',
          'Location permission is required to auto-fill. Please enter your city manually.',
          [{ text: 'OK', onPress: () => setIsManual(true) }]
        );
        return;
      }

      const location = await Location.getCurrentPositionAsync({});
      const { latitude, longitude } = location.coords;

      const reverseGeocode = await Location.reverseGeocodeAsync({
        latitude,
        longitude,
      });

      const city = reverseGeocode[0]?.city || reverseGeocode[0]?.subregion || 'Unknown City';
      const roundedLat = roundCoord(latitude);
      const roundedLon = roundCoord(longitude);

      if (!isValidLat(roundedLat) || !isValidLon(roundedLon)) {
        throw new Error('Invalid coordinates detected.');
      }

      setSearchQuery(city);
      onLocationSelect({ city, latitude: roundedLat, longitude: roundedLon });
      setIsManual(false);
    } catch (error) {
      console.error('Error getting location:', error);
      Alert.alert('Error', 'Could not fetch your location. Please try manual entry.');
      setIsManual(true);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async (query: string) => {
    setSearchQuery(query);
    if (query.length < 3) {
      setSuggestions([]);
      return;
    }

    try {
      const response = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(
          query
        )}&addressdetails=1&limit=5`,
        {
          headers: {
            'User-Agent': 'Bounswe2026Group7-MobileApp',
          },
        }
      );
      const data = await response.json();
      setSuggestions(data);
    } catch (error) {
      console.error('Nominatim search error:', error);
    }
  };

  const selectSuggestion = (item: any) => {
    const city = item.address.city || item.address.town || item.address.village || item.display_name.split(',')[0];
    const lat = parseFloat(item.lat);
    const lon = parseFloat(item.lon);
    
    const roundedLat = roundCoord(lat);
    const roundedLon = roundCoord(lon);

    if (!isValidLat(roundedLat) || !isValidLon(roundedLon)) {
      Alert.alert('Error', 'Invalid location coordinates selected.');
      return;
    }

    setSearchQuery(city);
    setSuggestions([]);
    onLocationSelect({
      city,
      latitude: roundedLat,
      longitude: roundedLon,
    });
  };

  const isMentor = roleTheme === 'mentor';
  const themeColor = isMentor ? '#456B50' : '#4B7B57';

  return (
    <View style={styles.container}>
      <Text style={styles.label}>Location</Text>
      
      {!isManual && !initialLocation?.city ? (
        <TouchableOpacity
          style={[styles.actionButton, { backgroundColor: themeColor }]}
          onPress={handleUseCurrentLocation}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#F8F6F2" />
          ) : (
            <Text style={styles.actionButtonText}>📍 Use My Current Location</Text>
          )}
        </TouchableOpacity>
      ) : null}

      <View style={styles.inputContainer}>
        <TextInput
          style={styles.input}
          placeholder="Search city manually..."
          placeholderTextColor="#B5ADA3"
          value={searchQuery}
          onChangeText={(text) => {
            setIsManual(true);
            handleSearch(text);
          }}
        />
        {loading && <ActivityIndicator style={styles.inputIcon} color={themeColor} />}
      </View>

      {suggestions.length > 0 && (
        <View style={styles.suggestionsContainer}>
          {suggestions.map((item, index) => (
            <TouchableOpacity
              key={index}
              style={styles.suggestionItem}
              onPress={() => selectSuggestion(item)}
            >
              <Text style={styles.suggestionText} numberOfLines={1}>
                {item.display_name}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      )}

      {initialLocation?.latitude !== undefined && (
        <Text style={styles.coordsText}>
          Selected: {initialLocation.city} ({initialLocation.latitude}, {initialLocation.longitude})
        </Text>
      )}

      <Text style={styles.privacyNote}>
        Note: Your coordinates are rounded to 2 decimal places (~1km precision) for privacy.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 20,
  },
  label: {
    color: '#7E7368',
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 8,
  },
  actionButton: {
    borderRadius: 18,
    paddingVertical: 14,
    alignItems: 'center',
    marginBottom: 12,
  },
  actionButtonText: {
    color: '#F8F6F2',
    fontWeight: '700',
    fontSize: 14,
  },
  inputContainer: {
    position: 'relative',
    justifyContent: 'center',
  },
  input: {
    height: 60,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    backgroundColor: '#FCFBF8',
    paddingHorizontal: 15,
    fontSize: 16,
    color: '#4A4138',
  },
  inputIcon: {
    position: 'absolute',
    right: 15,
  },
  suggestionsContainer: {
    backgroundColor: '#FCFBF8',
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    marginTop: 5,
    overflow: 'hidden',
    zIndex: 10,
  },
  suggestionItem: {
    padding: 15,
    borderBottomWidth: 1,
    borderBottomColor: '#F0EBE5',
  },
  suggestionText: {
    fontSize: 14,
    color: '#4A4138',
  },
  coordsText: {
    fontSize: 12,
    color: '#456B50',
    marginTop: 8,
    fontWeight: '600',
  },
  privacyNote: {
    fontSize: 11,
    color: '#9A8F82',
    marginTop: 6,
    fontStyle: 'italic',
  },
});
