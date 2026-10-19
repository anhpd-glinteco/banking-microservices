import React, { useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { QRCodeSVG } from 'qrcode.react';
import toast from 'react-hot-toast';
import './QRCode.scss';

const QRCodeGenerator = ({ account }) => {
    const { t } = useTranslation();
    const [amount, setAmount] = useState('');
    const [description, setDescription] = useState('');
    const qrRef = useRef(null);

    // QR data format: accountNumber|amount|description
    const qrData = JSON.stringify({
        accountNumber: account.accountNumber,
        accountName: account.accountName,
        amount: amount || '0',
        description: description || 'Payment request'
    });

    const handleDownload = () => {
        const svg = qrRef.current.querySelector('svg');
        const svgData = new XMLSerializer().serializeToString(svg);
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        const img = new Image();

        img.onload = () => {
            canvas.width = img.width;
            canvas.height = img.height;
            ctx.fillStyle = 'white';
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            ctx.drawImage(img, 0, 0);

            canvas.toBlob((blob) => {
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = `qr-${account.accountNumber}.png`;
                link.click();
                URL.revokeObjectURL(url);
                toast.success(t('qrDownloaded'));
            });
        };

        img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svgData)));
    };

    const handleCopyData = () => {
        navigator.clipboard.writeText(qrData);
        toast.success(t('qrCopied'));
    };

    return (
        <div className="qr-generator-card">
            <div className="card-header">
                <h3>📱 {t('generateQR')}</h3>
                <p>{t('createQRForPayment')}</p>
            </div>

            <div className="qr-preview" ref={qrRef}>
                <QRCodeSVG
                    value={qrData}
                    size={200}
                    level="H"
                    includeMargin={true}
                    bgColor="#ffffff"
                    fgColor="#000000"
                />
                <div className="qr-info">
                    <p><strong>{account.accountName}</strong></p>
                    <p className="account-number">{account.accountNumber}</p>
                    {amount && <p className="amount">Amount: {parseFloat(amount).toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}</p>}
                </div>
            </div>

            <div className="qr-options">
                <div className="input-group">
                    <label>{t('requestAmount')} (Optional)</label>
                    <div className="amount-input-wrapper">
                        <span className="currency">₫</span>
                        <input
                            type="number"
                            step="1000"
                            value={amount}
                            onChange={e => setAmount(e.target.value)}
                            placeholder="0"
                            min="0"
                        />
                    </div>
                </div>

                <div className="input-group">
                    <label>{t('description')} (Optional)</label>
                    <input
                        type="text"
                        value={description}
                        onChange={e => setDescription(e.target.value)}
                        placeholder={t('whatsThisFor')}
                        maxLength="50"
                    />
                </div>

                <div className="button-group">
                    <button className="btn-secondary" onClick={handleCopyData}>
                        📋 {t('copyData')}
                    </button>
                    <button className="btn-primary" onClick={handleDownload}>
                        💾 {t('downloadQR')}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default QRCodeGenerator;
