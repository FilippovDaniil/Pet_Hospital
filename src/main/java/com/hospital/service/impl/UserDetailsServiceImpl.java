package com.hospital.service.impl;

import com.hospital.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Реализация интерфейса Spring Security для загрузки данных пользователя по имени.
 *
 * UserDetailsService — один из ключевых интерфейсов Spring Security.
 * Его единственный метод loadUserByUsername() связывает Spring Security
 * с нашим источником данных (PostgreSQL через JPA).
 *
 * КАК ИСПОЛЬЗУЕТСЯ Spring Security:
 *
 *   1. При логине (/api/auth/login):
 *      authenticationManager.authenticate(UsernamePasswordAuthenticationToken)
 *          → DaoAuthenticationProvider
 *          → вызывает loadUserByUsername(username) ← мы здесь
 *          → получает User из БД
 *          → проверяет пароль через passwordEncoder.matches(raw, stored)
 *          → если OK → аутентификация успешна
 *
 *   2. В JwtAuthenticationFilter (на каждый запрос с токеном):
 *      userDetailsService.loadUserByUsername(username)
 *          → мы возвращаем User из БД
 *          → фильтр проверяет актуальность данных (роли могли измениться)
 *
 * Так как User реализует UserDetails, мы возвращаем его напрямую —
 * никакого адаптера не нужно.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Загружает пользователя по имени (логину) из базы данных.
     *
     * @param username логин пользователя (из токена или из формы входа)
     * @return UserDetails (наш User реализует этот интерфейс напрямую)
     * @throws UsernameNotFoundException если пользователь не найден в БД.
     *         Spring Security перехватит это исключение и вернёт HTTP 401 Unauthorized.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + username));
    }
}
