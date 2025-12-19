import toast from 'react-hot-toast';
import React from 'react';
import {
    CheckCircleFilled,
    CloseCircleFilled,
    InfoCircleFilled,
    ExclamationCircleFilled,
    LoadingOutlined
} from '@ant-design/icons';

const toastStyle = {
    display: 'flex',
    alignItems: 'center',
    background: '#fff',
    color: '#333',
    padding: '12px 16px',
    borderRadius: '12px',
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.15)',
    minWidth: '300px',
    maxWidth: '450px',
    border: '1px solid #f0f0f0',
};

const iconStyle = {
    fontSize: '20px',
    marginRight: '12px',
};

const contentStyle = {
    display: 'flex',
    flexDirection: 'column',
};

const titleStyle = {
    fontWeight: 600,
    fontSize: '15px',
    marginBottom: '2px',
};

const messageStyle = {
    fontSize: '13px',
    color: '#666',
};

export const showNotification = {
    success: (title, message, options = {}) => {
        return toast.custom((t) => (
            <div
                style={{
                    ...toastStyle,
                    animation: t.visible ? 'custom-enter 0.5s ease' : 'custom-exit 0.5s ease',
                }}
            >
                <CheckCircleFilled style={{ ...iconStyle, color: '#52c41a' }} />
                <div style={contentStyle}>
                    <span style={titleStyle}>{title}</span>
                    {message && <span style={messageStyle}>{message}</span>}
                </div>
            </div>
        ), { duration: 3000, ...options });
    },
    error: (title, message, options = {}) => {
        return toast.custom((t) => (
            <div
                style={{
                    ...toastStyle,
                    animation: t.visible ? 'custom-enter 0.5s ease' : 'custom-exit 0.5s ease',
                }}
            >
                <CloseCircleFilled style={{ ...iconStyle, color: '#ff4d4f' }} />
                <div style={contentStyle}>
                    <span style={titleStyle}>{title}</span>
                    {message && <span style={messageStyle}>{message}</span>}
                </div>
            </div>
        ), { duration: 4000, ...options });
    },
    info: (title, message, options = {}) => {
        return toast.custom((t) => (
            <div
                style={{
                    ...toastStyle,
                    animation: t.visible ? 'custom-enter 0.5s ease' : 'custom-exit 0.5s ease',
                }}
            >
                <InfoCircleFilled style={{ ...iconStyle, color: '#1890ff' }} />
                <div style={contentStyle}>
                    <span style={titleStyle}>{title}</span>
                    {message && <span style={messageStyle}>{message}</span>}
                </div>
            </div>
        ), { duration: 3000, ...options });
    },
    warning: (title, message, options = {}) => {
        return toast.custom((t) => (
            <div
                style={{
                    ...toastStyle,
                    animation: t.visible ? 'custom-enter 0.5s ease' : 'custom-exit 0.5s ease',
                }}
            >
                <ExclamationCircleFilled style={{ ...iconStyle, color: '#faad14' }} />
                <div style={contentStyle}>
                    <span style={titleStyle}>{title}</span>
                    {message && <span style={messageStyle}>{message}</span>}
                </div>
            </div>
        ), { duration: 3000, ...options });
    },
    loading: (title, message, options = {}) => {
        return toast.custom((t) => (
            <div
                style={{
                    ...toastStyle,
                    animation: t.visible ? 'custom-enter 0.5s ease' : 'custom-exit 0.5s ease',
                }}
            >
                <LoadingOutlined style={{ ...iconStyle, color: '#1890ff' }} spin />
                <div style={contentStyle}>
                    <span style={titleStyle}>{title}</span>
                    {message && <span style={messageStyle}>{message}</span>}
                </div>
            </div>
        ), { duration: Infinity, ...options });
    },
    custom: (content, options) => {
        return toast.custom((t) => (
            <div
                style={{
                    ...toastStyle,
                    animation: t.visible ? 'custom-enter 0.5s ease' : 'custom-exit 0.5s ease',
                    padding: '16px' // slightly more padding for custom content
                }}
            >
                {content}
            </div>
        ), options);
    }
};
