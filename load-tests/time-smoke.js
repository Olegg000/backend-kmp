import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 20,              // количество одновременных виртуальных пользователей
    duration: '30s',      // длительность теста
    thresholds: {
        http_req_failed: ['rate<0.01'],      // меньше 1% запросов с ошибкой
        http_req_duration: ['p(95)<500'],    // 95% запросов быстрее 500мс
    },
};

// BASE_URL берём из переменной среды или по умолчанию http://app:8080 (имя сервиса в docker-compose)
const BASE_URL = __ENV.BASE_URL || 'http://app:8080';

export default function () {
    const res = http.get(`${BASE_URL}/api/v1/time/current`);

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    // небольшая пауза между запросами от одного виртуального пользователя
    sleep(1);
}