import React, { createContext, useContext, useState, useEffect } from 'react';
import * as SecureStore from 'expo-secure-store';

type UserRole = 'mentee' | 'mentor' | 'admin';

type RoleContextType = {
  role: UserRole;
  setRole: (role: UserRole) => void;
  clearRole: () => void;
};

const RoleContext = createContext<RoleContextType | undefined>(undefined);

export function RoleProvider({ children }: { children: React.ReactNode }) {
  const [role, setRoleState] = useState<UserRole>('mentee');

  useEffect(() => {
    SecureStore.getItemAsync('userRole').then((saved) => {
      if (saved === 'mentor' || saved === 'mentee' || saved === 'admin') {
        setRoleState(saved);
      }
    });
  }, []);

  const setRole = (newRole: UserRole) => {
    setRoleState(newRole);
    SecureStore.setItemAsync('userRole', newRole);
  };

  const clearRole = () => {
    setRoleState('mentee');
    SecureStore.deleteItemAsync('userRole');
  };

  return (
    <RoleContext.Provider value={{ role, setRole, clearRole }}>
      {children}
    </RoleContext.Provider>
  );
}

export function useRole() {
  const context = useContext(RoleContext);
  if (!context) throw new Error('useRole must be used inside RoleProvider');
  return context;
}
