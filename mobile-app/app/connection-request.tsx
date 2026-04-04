import React, { useMemo, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
} from 'react-native';

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

export default function ConnectionRequestScreen() {
  const params = useLocalSearchParams();

  const mode = parseString(params.mode) as 'meeting' | 'change' | 'end';
  const targetName = parseString(params.targetName);
  const targetType = parseString(params.targetType);

  const [title, setTitle] = useState('');
  const [dateOrSlot, setDateOrSlot] = useState('');
  const [details, setDetails] = useState('');

  const config = useMemo(() => {
    switch (mode) {
      case 'meeting':
        return {
          screenTitle: 'Setup Meeting Request',
          titlePlaceholder: 'Meeting title',
          datePlaceholder: 'Preferred date / time',
          detailsPlaceholder: 'Write your meeting request details',
          buttonText: 'Send Meeting Request',
        };
      case 'change':
        return {
          screenTitle: 'Change Request',
          titlePlaceholder: 'What should change?',
          datePlaceholder: 'Preferred new date / slot',
          detailsPlaceholder: 'Explain the requested change',
          buttonText: 'Send Change Request',
        };
      default:
        return {
          screenTitle: 'End Mentorship',
          titlePlaceholder: 'Reason title',
          datePlaceholder: 'Optional effective date',
          detailsPlaceholder: 'Explain why you want to end the mentorship',
          buttonText: 'Send End Request',
        };
    }
  }, [mode]);

  const handleSubmit = () => {
    Alert.alert(
      'Request Sent',
      `${config.screenTitle} has been sent to ${targetName || targetType || 'the user'}.`
    );
    router.back();
  };

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <Text style={styles.statusText}>9:41</Text>
          <TouchableOpacity onPress={() => router.back()}>
            <Text style={styles.backText}>‹ Back</Text>
          </TouchableOpacity>

          <Text style={styles.title}>{config.screenTitle}</Text>
          <Text style={styles.subtitle}>
            Send a request regarding your connection with {targetName || 'this user'}.
          </Text>
        </View>

        <View style={styles.formCard}>
          <Text style={styles.label}>TITLE</Text>
          <TextInput
            style={styles.input}
            value={title}
            onChangeText={setTitle}
            placeholder={config.titlePlaceholder}
            placeholderTextColor="#B5ADA3"
          />

          <Text style={styles.label}>DATE / SLOT</Text>
          <TextInput
            style={styles.input}
            value={dateOrSlot}
            onChangeText={setDateOrSlot}
            placeholder={config.datePlaceholder}
            placeholderTextColor="#B5ADA3"
          />

          <Text style={styles.label}>DETAILS</Text>
          <TextInput
            style={[styles.input, styles.bigInput]}
            value={details}
            onChangeText={setDetails}
            placeholder={config.detailsPlaceholder}
            placeholderTextColor="#B5ADA3"
            multiline
            textAlignVertical="top"
          />
        </View>

        <TouchableOpacity style={styles.primaryButton} onPress={handleSubmit}>
          <Text style={styles.primaryButtonText}>{config.buttonText}</Text>
        </TouchableOpacity>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },
  scrollContent: {
    padding: 24,
    paddingTop: 54,
    paddingBottom: 36,
  },
  header: {
    marginBottom: 20,
  },
  statusText: {
    color: '#456B50',
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 14,
  },
  backText: {
    color: '#456B50',
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 14,
  },
  title: {
    color: '#23372B',
    fontSize: 30,
    fontWeight: '700',
    marginBottom: 8,
  },
  subtitle: {
    color: '#8B8176',
    fontSize: 14,
    lineHeight: 21,
  },
  formCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 22,
    marginBottom: 20,
  },
  label: {
    color: '#7E7368',
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 10,
  },
  input: {
    height: 64,
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    backgroundColor: '#FCFBF8',
    paddingHorizontal: 18,
    fontSize: 16,
    color: '#4A4138',
    marginBottom: 22,
  },
  bigInput: {
    height: 130,
    paddingTop: 18,
    marginBottom: 0,
  },
  primaryButton: {
    backgroundColor: '#4B7B57',
    borderRadius: 24,
    paddingVertical: 18,
    alignItems: 'center',
  },
  primaryButtonText: {
    color: '#F8F6F2',
    fontSize: 16,
    fontWeight: '700',
  },
});