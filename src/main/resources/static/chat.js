class ChatApp {
    constructor() {
        this.sessionId = this.generateSessionId();
        this.userName = '用户' + Math.floor(Math.random() * 1000);
        this.ws = null;
        this.isTyping = false;
        this.currentHumanReviewMessageId = null;

        this.init();
    }

    init() {
        this.initWebSocket();
        this.initEventListeners();
        this.focusInput();
    }

    generateSessionId() {
        return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    }

    initWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/chat`;

        this.ws = new WebSocket(wsUrl);

        this.ws.onopen = () => {
            console.log('WebSocket连接已建立');
            this.updateStatus('在线');
        };

        this.ws.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                this.handleWebSocketMessage(message);
            } catch (error) {
                console.error('解析WebSocket消息失败:', error);
            }
        };

        this.ws.onclose = () => {
            console.log('WebSocket连接已关闭');
            this.updateStatus('离线');
            // 尝试重连
            setTimeout(() => this.initWebSocket(), 3000);
        };

        this.ws.onerror = (error) => {
            console.error('WebSocket错误:', error);
            this.updateStatus('连接错误');
        };
    }

    handleWebSocketMessage(message) {
        switch (message.type) {
            case 'status':
                if (message.content === 'connected') {
                    this.updateStatus('在线');
                }
                break;
            case 'human_review':
                this.showHumanReviewModal(message);
                break;
            case 'typing':
                this.handleTypingIndicator(message);
                break;
        }
    }

    initEventListeners() {
        const messageInput = document.getElementById('message-input');
        const sendButton = document.getElementById('send-button');

        // 发送按钮点击事件
        sendButton.addEventListener('click', () => this.sendMessage());

        // 输入框回车事件
        messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });

        // 输入框输入事件（用于显示正在输入状态）
        messageInput.addEventListener('input', () => {
            if (!this.isTyping && messageInput.value.trim()) {
                this.isTyping = true;
                this.sendTypingStatus(true);
            } else if (this.isTyping && !messageInput.value.trim()) {
                this.isTyping = false;
                this.sendTypingStatus(false);
            }
        });

        // 失去焦点时停止打字状态
        messageInput.addEventListener('blur', () => {
            if (this.isTyping) {
                this.isTyping = false;
                this.sendTypingStatus(false);
            }
        });
    }

    async sendMessage() {
        const messageInput = document.getElementById('message-input');
        const sendButton = document.getElementById('send-button');
        const message = messageInput.value.trim();

        if (!message) return;

        // 禁用发送按钮
        sendButton.disabled = true;
        messageInput.disabled = true;

        // 添加用户消息到界面
        this.addMessage({
            type: 'user',
            content: message,
            status: 'sent',
            timestamp: Date.now()
        });

        // 清空输入框
        messageInput.value = '';

        // 显示正在输入指示器
        this.showTypingIndicator();

        try {
            const response = await fetch('/api/chat/send', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    message: message,
                    userName: this.userName,
                    sessionId: this.sessionId
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const assistantMessage = await response.json();

            // 隐藏正在输入指示器
            this.hideTypingIndicator();

            // 添加助手回复
            this.addMessage(assistantMessage);

            // 如果需要人工审核，显示模态框
            if (assistantMessage.status === 'waiting_human') {
                this.currentHumanReviewMessageId = assistantMessage.id;
                this.showHumanReviewModal({
                    content: assistantMessage.content,
                    classification: assistantMessage.classification
                });
            }

        } catch (error) {
            console.error('发送消息失败:', error);
            this.hideTypingIndicator();
            this.addMessage({
                type: 'assistant',
                content: '抱歉，发送消息时出现错误。请稍后再试。',
                status: 'error',
                timestamp: Date.now()
            });
        } finally {
            // 重新启用输入
            sendButton.disabled = false;
            messageInput.disabled = false;
            this.focusInput();
        }
    }

    addMessage(message) {
        const messagesContainer = document.getElementById('messages');
        const messageElement = document.createElement('div');
        messageElement.className = `message ${message.type}`;
        messageElement.dataset.messageId = message.id || '';

        const avatar = message.type === 'user' ? '👤' : '🤖';
        const time = this.formatTime(message.timestamp);

        let statusHtml = '';
        if (message.status && message.type === 'assistant') {
            const statusClass = `status-${message.status.replace('_', '-')}`;
            const statusText = this.getStatusText(message.status);
            statusHtml = `
                <div class="message-status">
                    <span class="status-indicator ${statusClass}"></span>
                    ${statusText}
                </div>
            `;
        }

        // 格式化消息内容
        let formattedContent;
        if (message.type === 'assistant') {
            // AI助手的消息使用markdown格式化
            formattedContent = this.formatMessageContent(message.content);
        } else {
            // 用户消息保持原样，只转义HTML并处理换行
            formattedContent = this.escapeHtml(message.content).replace(/\n/g, '<br>');
        }

        messageElement.innerHTML = `
            <div class="message-avatar">${avatar}</div>
            <div class="message-content">
                <div class="message-text">${formattedContent}</div>
                <div class="message-time">${time}</div>
                ${statusHtml}
            </div>
        `;

        messagesContainer.appendChild(messageElement);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    showTypingIndicator() {
        const indicator = document.getElementById('typing-indicator');
        indicator.classList.add('show');
        this.scrollToBottom();
    }

    hideTypingIndicator() {
        const indicator = document.getElementById('typing-indicator');
        indicator.classList.remove('show');
    }

    showHumanReviewModal(message) {
        const modal = document.getElementById('human-review-modal');
        modal.classList.add('show');

        // 可以在这里显示具体的审核内容
        console.log('需要人工审核的消息:', message);
    }

    hideHumanReviewModal() {
        const modal = document.getElementById('human-review-modal');
        modal.classList.remove('show');
    }

    async handleHumanReview(feedback) {
        if (!this.currentHumanReviewMessageId) return;

        this.hideHumanReviewModal();

        try {
            const response = await fetch(`/api/chat/resume?sessionId=${this.sessionId}&feedback=${feedback}`, {
                method: 'POST'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();

            // 更新之前的消息状态
            this.updateMessageStatus(this.currentHumanReviewMessageId, 'completed');

            // 添加最终回复
            this.addMessage(result);

        } catch (error) {
            console.error('处理人工审核失败:', error);
            this.addMessage({
                type: 'assistant',
                content: '处理人工审核时出现错误，请稍后再试。',
                status: 'error',
                timestamp: Date.now()
            });
        }

        this.currentHumanReviewMessageId = null;
    }

    updateMessageStatus(messageId, newStatus) {
        const messageElement = document.querySelector(`[data-message-id="${messageId}"]`);
        if (messageElement) {
            const statusElement = messageElement.querySelector('.message-status');
            if (statusElement) {
                const statusClass = `status-${newStatus.replace('_', '-')}`;
                const statusText = this.getStatusText(newStatus);
                statusElement.className = `message-status`;
                statusElement.innerHTML = `
                    <span class="status-indicator ${statusClass}"></span>
                    ${statusText}
                `;
            }
        }
    }

    sendTypingStatus(isTyping) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({
                type: 'typing',
                sessionId: this.sessionId,
                userName: this.userName,
                timestamp: Date.now()
            }));
        }
    }

    handleTypingIndicator(message) {
        // 这里可以实现显示其他用户正在输入的功能
        console.log('用户打字状态:', message);
    }

    updateStatus(status) {
        const statusElement = document.getElementById('status-text');
        statusElement.textContent = status;
    }

    focusInput() {
        const messageInput = document.getElementById('message-input');
        messageInput.focus();
    }

    scrollToBottom() {
        const messagesContainer = document.getElementById('messages');
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    formatTime(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);

        if (diffMins < 1) return '刚刚';
        if (diffMins < 60) return `${diffMins}分钟前`;

        return date.toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    getStatusText(status) {
        const statusMap = {
            'sending': '发送中',
            'sent': '已发送',
            'waiting_human': '等待人工审核',
            'completed': '已完成',
            'error': '发送失败'
        };
        return statusMap[status] || status;
    }

    formatMessageContent(content) {
        if (!content) return '';

        // 配置marked选项
        marked.setOptions({
            breaks: true, // 支持换行符
            gfm: true,    // 支持GitHub Flavored Markdown
            sanitize: false, // 我们会自己处理安全性
            smartLists: true,
            smartypants: true
        });

        // 预处理：清理一些常见的格式问题
        let processedContent = content
            // 处理多余的星号
            .replace(/\*{3,}/g, '**')
            // 处理列表格式
            .replace(/^(\s*)-\s+/gm, '$1• ')
            // 确保列表项之间有正确的换行
            .replace(/([^\n])\n(\s*)•/g, '$1\n\n$2•')
            // 处理编号列表
            .replace(/^(\s*)(\d+)\.\s+/gm, (match, space, num) => {
                return space + num + '. ';
            });

        try {
            // 解析markdown
            const html = marked.parse(processedContent);
            return html;
        } catch (error) {
            console.warn('Markdown解析失败:', error);
            // 如果解析失败，至少处理换行
            return this.escapeHtml(content).replace(/\n/g, '<br>');
        }
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// 全局函数，供HTML调用
function handleHumanReview(feedback) {
    if (window.chatApp) {
        window.chatApp.handleHumanReview(feedback);
    }
}

// 初始化聊天应用
document.addEventListener('DOMContentLoaded', () => {
    window.chatApp = new ChatApp();
});