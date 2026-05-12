import React from 'react';
import {
  Modal,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  TouchableWithoutFeedback,
} from 'react-native';

export type ModalField = {
  label: string;
  placeholder: string;
  value: string;
  onChange: (v: string) => void;
  keyboardType?: 'default' | 'number-pad' | 'numeric';
  multiline?: boolean;
  required?: boolean;
};

type Props = {
  visible: boolean;
  title: string;
  message?: string;
  fields?: ModalField[];
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  testID?: string;
};

export default function ActionModal({
  visible,
  title,
  message,
  fields = [],
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  danger = false,
  loading = false,
  onConfirm,
  onCancel,
  testID,
}: Props) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <KeyboardAvoidingView
        style={styles.overlay}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <TouchableWithoutFeedback onPress={onCancel}>
          <View style={styles.backdrop} />
        </TouchableWithoutFeedback>

        <View style={styles.sheet} testID={testID ? `${testID}.sheet` : undefined}>
          <View style={styles.handle} />

          <Text style={styles.title}>{title}</Text>

          {!!message && <Text style={styles.message}>{message}</Text>}

          {fields.map((field, i) => (
            <View key={i} style={styles.fieldWrap}>
              <Text style={styles.fieldLabel}>
                {field.label}
                {field.required ? ' *' : ''}
              </Text>
              <TextInput
                style={[styles.input, field.multiline && styles.inputMulti]}
                value={field.value}
                onChangeText={field.onChange}
                placeholder={field.placeholder}
                placeholderTextColor="#B0A89E"
                keyboardType={field.keyboardType ?? 'default'}
                multiline={field.multiline}
                textAlignVertical={field.multiline ? 'top' : 'center'}
                editable={!loading}
                testID={testID ? `${testID}.field-${i}` : undefined}
              />
            </View>
          ))}

          <View style={styles.btnRow}>
            <TouchableOpacity
              style={styles.cancelBtn}
              onPress={onCancel}
              disabled={loading}
              testID={testID ? `${testID}.cancel` : undefined}
            >
              <Text style={styles.cancelBtnText}>{cancelLabel}</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.confirmBtn, danger && styles.confirmBtnDanger, loading && { opacity: 0.6 }]}
              onPress={onConfirm}
              disabled={loading}
              testID={testID ? `${testID}.confirm` : undefined}
            >
              {loading ? (
                <ActivityIndicator size="small" color="#fff" />
              ) : (
                <Text style={styles.confirmBtnText}>{confirmLabel}</Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  sheet: {
    backgroundColor: '#F8F6F2',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 40,
  },
  handle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#D5CBC0',
    alignSelf: 'center',
    marginBottom: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1D1D38',
    marginBottom: 8,
  },
  message: {
    fontSize: 14,
    color: '#6E655A',
    lineHeight: 20,
    marginBottom: 16,
  },
  fieldWrap: {
    marginBottom: 14,
  },
  fieldLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: '#7E7368',
    marginBottom: 6,
    letterSpacing: 0.5,
  },
  input: {
    backgroundColor: '#FCFBF8',
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 15,
    color: '#2B2B2B',
  },
  inputMulti: {
    minHeight: 90,
    paddingTop: 12,
  },
  btnRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 8,
  },
  cancelBtn: {
    flex: 1,
    backgroundColor: '#EDE8E1',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  cancelBtnText: {
    fontSize: 15,
    fontWeight: '700',
    color: '#6B6158',
  },
  confirmBtn: {
    flex: 1,
    backgroundColor: '#456B50',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  confirmBtnDanger: {
    backgroundColor: '#D9534F',
  },
  confirmBtnText: {
    fontSize: 15,
    fontWeight: '700',
    color: '#fff',
  },
});
