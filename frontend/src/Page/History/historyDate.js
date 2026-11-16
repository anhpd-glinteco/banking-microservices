const hasTimezone = (value) => /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value);

export const parseHistoryTimestamp = (value) =>
    new Date(hasTimezone(value) ? value : `${value}Z`);

export const formatHistoryTimestamp = (date) => date.toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
});
