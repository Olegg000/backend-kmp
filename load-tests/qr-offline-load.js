import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4, randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
    vus: 20,              // количество одновременных виртуальных пользователей
    duration: '30s',      // длительность теста
    thresholds: {
        http_req_failed: ['rate<0.01'],      // <1% запросов с ошибкой по сети/5xx
        http_req_duration: ['p(95)<800'],    // 95% ответов быстрее 800мс
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://app:8080';
const MEAL_TYPES = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK', 'SPECIAL'];

function randomMealType() {
    return MEAL_TYPES[Math.floor(Math.random() * MEAL_TYPES.length)];
}

// Небольшой base64‑похожий рандом для nonce / signature
function randomBase64(len) {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    let out = '';
    for (let i = 0; i < len; i++) {
        out += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return out;
}

export default function () {
    const payload = {
        userId: uuidv4(),                              // случайный UUID (скорее всего несуществующий студент)
        timestamp: Math.floor(Date.now() / 1000),      // текущий unix time
        mealType: randomMealType(),                    // случайный тип питания
        nonce: randomString(16),                       // любой nonce
        signature: randomBase64(64),                   // псевдо base64-подпись
    };

    const res = http.post(
        `${BASE_URL}/api/v1/qr/validate-offline`,
        JSON.stringify(payload),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    // небольшая пауза между запросами от одного VU
    sleep(1);
}