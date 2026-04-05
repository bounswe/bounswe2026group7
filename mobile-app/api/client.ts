import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

// Kendi bilgisayarının IPv4 adresini buraya yazmalısın. 
// Örnek: 'http://192.168.1.45:8080/api'
const apiClient = axios.create({
  baseURL: 'http://192.168.1.134:8080/api', 
  headers: {
    'Content-Type': 'application/json',
  },
});


// Interceptor: Her istek gitmeden önce araya girer
apiClient.interceptors.request.use(
  async (config) => {
    // YENİ EKLENEN KISIM: Eğer istek /auth/ ile başlıyorsa (login veya register) 
    // token aramaya çalışma, direkt isteği yolla!
    if (config.url && config.url.includes('/auth/')) {
      return config;
    }

    // Auth dışındaki sayfalar (profil vb.) için token'ı ekle
    const token = await SecureStore.getItemAsync('userToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export default apiClient;