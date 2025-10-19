# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java Spring Boot application that implements an AI-powered customer support agent using LangGraph4j and Spring AI. The system uses a state-based conversational architecture with human-in-the-loop capabilities for automated message classification and response generation. It features a modern web-based chat interface with real-time WebSocket communication.

## Tech Stack

- **Java 17** with **Spring Boot 3.5.6**
- **LangGraph4j 1.7.0-beta3** for state graph execution
- **Spring AI 1.0.3** with OpenAI/DeepSeek integration
- **Spring WebSocket** for real-time communication
- **Maven** for build management
- **Lombok** for reduced boilerplate
- **Marked.js** for frontend markdown rendering
- **Modern HTML5/CSS3/JavaScript** for responsive chat interface

## Common Development Commands

```bash
# Build the project
mvn clean package

# Run the application
mvn spring-boot:run

# Run tests
mvn test

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Build for production
mvn clean package -DskipTests
java -jar target/customer-support-agent-1.0-SNAPSHOT.jar
```

## Architecture

### Core Components

The application follows a **State Graph Architecture** using LangGraph4j:

- **CustomerSupportGraph**: Main state graph orchestrating the workflow
- **MessageAgentState**: State management for conversation context
- **Node-based Processing**: Each workflow step is a separate node implementing `CommandAction`

### Workflow Nodes

1. **ClassifyIntentCmdNode**: Classifies messages by intent (QUESTION, BUG, BILLING, FEATURE, COMPLEX) and urgency (LOW, MEDIUM, HIGH, CRITICAL)
2. **Path Selection**: Routes based on classification:
   - `BILLING` or `CRITICAL` → Human review
   - `QUESTION` or `FEATURE` → Documentation search
   - `BUG` → Bug tracking
   - Default → Response generation
3. **SearchDocumentationCmdNode**: Searches for relevant help content (currently mocked)
4. **DraftResponseCmdNode**: Creates AI-generated responses
5. **HumanReviewCmdNode**: Pauses workflow for human approval on critical cases
6. **BugTrackingCmdNode**: Creates bug tickets (currently mocked)

### Web Application Layer

1. **ChatController**: REST API endpoints for chat functionality
   - `POST /api/chat/send`: Send user messages and get AI responses
   - `POST /api/chat/resume`: Resume paused workflows with human feedback
   - `GET /api/chat/session/{sessionId}`: Get session information
   - `POST /api/chat/session/{sessionId}/typing`: Update typing status

2. **WebSocket Layer**:
   - **ChatWebSocketHandler**: Handles real-time WebSocket connections
   - **WebSocketConfig**: Configures WebSocket endpoints with CORS
   - Supports typing indicators, status updates, and human review notifications

3. **Frontend Application**:
   - **Modern Chat UI**: Responsive design with gradient backgrounds and animations
   - **Real-time Communication**: WebSocket client for live updates
   - **Markdown Rendering**: AI responses formatted with proper typography
   - **Session Management**: Client-side session tracking and state management

### Key Patterns

- **State Machine Pattern**: LangGraph4j manages state transitions
- **Command Pattern**: Nodes return commands to control flow
- **Human-in-the-Loop**: Critical decisions paused for manual review
- **Retry Mechanism**: `@Retryable` annotations for resilient API calls

## Configuration

- **Application Properties**: `src/main/resources/application.properties`
- **AI Model**: Uses DeepSeek API (`deepseek-chat` model)
- **Temperature**: 1.0 for creative responses
- **Logging**: DEBUG level for Spring AI packages

## Web Interface

### Chat Application
- **Main Chat Interface**: Available at `http://localhost:8080`
- **Real-time Communication**: WebSocket endpoint at `/ws/chat`
- **Modern UI**: Responsive design with typing indicators, message status, and human review modals
- **Markdown Support**: AI responses are rendered with proper formatting (lists, bold text, code blocks)

### Chat API Endpoints

```bash
# Send a message
curl -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -d '{"message": "Your message here", "userName": "username", "sessionId": "session123"}'

# Resume paused workflow with human feedback
curl -X POST "http://localhost:8080/api/chat/resume?sessionId=session123&feedback=approve"

# Get session information
curl http://localhost:8080/api/chat/session/session123
```

### Legacy Demo Endpoints

The `DemoController` provides legacy test endpoints:

```bash
# Run the customer support workflow
curl http://localhost:8080/run

# Resume paused workflow with human feedback
curl "http://localhost:8080/resume?feedback=approve"  # or "reject"
```

## Frontend Architecture

### Chat Interface Components
- **ChatController**: REST API endpoints for message handling (`/api/chat/*`)
- **ChatWebSocketHandler**: WebSocket handler for real-time communication (`/ws/chat`)
- **WebSocketConfig**: WebSocket configuration with CORS support
- **Frontend Files**:
  - `src/main/resources/static/index.html`: Main chat interface
  - `src/main/resources/static/chat.js`: Chat application logic with markdown support
  - Integrated Marked.js library for markdown rendering

### Chat Features
- **Session Management**: Persistent conversation state with unique session IDs
- **Message Formatting**: AI responses rendered with markdown (lists, bold, code blocks)
- **Real-time Updates**: WebSocket communication for typing indicators and status updates
- **Human Review Workflow**: Modal interface for critical message approval
- **Responsive Design**: Mobile-friendly chat interface with modern styling

## Current Limitations

- Documentation search and bug tracking nodes are mocked implementations
- No unit or integration tests exist
- API keys are hardcoded in properties (should use environment variables)
- No authentication or input validation on endpoints
- No CI/CD configuration
- WebSocket connections require proper session cleanup on disconnection

## Development Notes

- The workflow automatically pauses for human review on billing or critical urgency messages
- State is maintained between calls - use `/api/chat/resume` endpoint to continue paused workflows
- Spring Retry is configured for external API calls to handle transient failures
- Logback configuration provides detailed logging for debugging AI interactions
- Frontend uses modern JavaScript with proper error handling and fallback mechanisms
- WebSocket messages are JSON-formatted with type, content, and metadata fields
- Chat interface supports both REST API calls and WebSocket real-time updates

## Usage Guide

### Getting Started

1. **Start the Application**:
   ```bash
   mvn spring-boot:run
   ```

2. **Access the Chat Interface**:
   - Open browser to `http://localhost:8080`
   - The chat interface will load automatically

3. **Send Messages**:
   - Type your message in the input field
   - Press Enter or click the send button
   - AI will respond with formatted markdown content

4. **Human Review Process**:
   - Critical messages (BILLING or CRITICAL) trigger human review
   - A modal appears asking for approval/rejection
   - The workflow resumes based on human feedback

### Message Classification

The system automatically classifies incoming messages:

- **QUESTION**: General inquiries → Direct AI response
- **BILLING**: Payment/billing issues → Human review required
- **BUG**: Technical problems → Bug tracking (mocked)
- **FEATURE**: Feature requests → Documentation search (mocked)
- **COMPLEX**: Complex issues → Direct AI response

Urgency levels: **LOW**, **MEDIUM**, **HIGH**, **CRITICAL**

### File Structure

```
src/main/
├── java/com/github/zavier/customer/support/
│   ├── agent/                 # AI workflow and nodes
│   ├── config/               # Configuration classes
│   ├── constant/             # Enum definitions
│   └── web/                  # Web controllers and handlers
└── resources/
    ├── static/
    │   ├── index.html        # Chat interface
    │   └── chat.js          # Frontend application logic
    └── application.properties # Spring configuration
```