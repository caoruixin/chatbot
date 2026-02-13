# Testing Guide - Phase 1 Human IM Flow

## Services Status

✅ **Backend**: Running on http://localhost:8080
✅ **Frontend**: Running on http://localhost:3000
✅ **Database**: PostgreSQL with tables created
✅ **GetStream**: API verified

## Test Scenario: User → Human Agent Chat

### Step 1: Open User Web (Customer)

1. Open your browser: http://localhost:3000/chat?userId=user_alice
   - The URL parameter `userId=user_alice` identifies you as "Alice"
   - You can also try `user_bob` or `user_carol`

2. You should see:
   - Empty chat interface
   - Message input box at the bottom
   - "Send" button

### Step 2: Send First Message as User

1. Type a message in the input box, for example:
   ```
   你好，我想查询我的帖子状态
   ```

2. Click "Send" or press Enter

3. **What happens behind the scenes:**
   - Frontend calls `POST /api/messages/inbound`
   - Backend creates a new **Conversation** (first-time user)
   - Backend creates a new **Session** with status=`AI_HANDLING`
   - Backend creates a GetStream channel
   - Backend saves the message to database
   - Backend routes to AI (placeholder responds: "AI 客服功能即将上线，请发送'转人工'联系人工客服")
   - Message appears in GetStream channel

4. You should see:
   - Your message on the right (blue bubble)
   - AI placeholder message on the left (green bubble)

### Step 3: Request Human Agent

1. Send this magic keyword:
   ```
   转人工
   ```

2. **What happens:**
   - Router detects "转人工" keyword
   - Session status changes to `HUMAN_HANDLING`
   - Human agent (`agent_default`) is added to the GetStream channel
   - System message appears: "正在为您转接人工客服，请稍候..."

### Step 4: Open Agent Dashboard (Human Agent)

1. In a **new browser tab/window**, open: http://localhost:3000/agent

2. You should see:
   - Left sidebar: "Active Sessions" list
   - Your session should appear with:
     - Session ID
     - Status: `HUMAN_HANDLING` (orange badge)
     - Conversation ID
     - Last activity time

3. Click on the session to select it

4. You should see:
   - Full message history (user messages + AI placeholder + system message)
   - Message input box at bottom
   - Three tool buttons: FAQ查询, 帖子查询, 删除数据

### Step 5: Agent Sends Reply

1. In the agent dashboard, type a response, for example:
   ```
   您好！我是人工客服，很高兴为您服务。您想查询哪个用户的帖子状态？
   ```

2. Click "Send"

3. **What happens:**
   - Frontend calls `POST /api/messages/agent-reply`
   - Backend saves message with sender_type=`HUMAN_AGENT`
   - Backend sends via GetStream
   - Message appears in both User Web and Agent Web

4. Switch back to **User Web tab** - you should see the agent's message arrive in real-time!

### Step 6: Continued Conversation

1. In User Web, reply:
   ```
   我想查询 user_alice 的帖子
   ```

2. In Agent Web, use the **帖子查询** tool:
   - Click "帖子查询" button
   - Enter username: `user_alice`
   - Click search/submit
   - You'll see results showing Alice's posts (from mock data):
     - "如何重置密码" - PUBLISHED
     - "账号被锁定" - UNDER_REVIEW
     - "修改绑定邮箱" - PUBLISHED

3. Agent types a response based on the tool results:
   ```
   查询到您有3个帖子：
   1. 如何重置密码 - 已发布
   2. 账号被锁定 - 审核中
   3. 修改绑定邮箱 - 已发布

   需要我帮您处理审核中的帖子吗？
   ```

4. Send the message - it appears in User Web

### Step 7: Test Other Tools (Optional)

**FAQ查询 (Placeholder in Phase 1):**
- Click "FAQ查询" button
- Enter a question
- Returns empty result (FAQ search will be implemented in Phase 2)

**删除数据 (Mock):**
- Click "删除数据" button
- Enter username: `user_bob`
- Mock response: "用户 user_bob 的数据已标记为删除"
- Request ID is generated

### Step 8: Test Session Timeout

1. Wait 10+ minutes without sending any messages
2. The session will automatically close (status → `CLOSED`)
3. If user sends a new message, a **new session** is created with status=`AI_HANDLING`
4. The flow starts over (AI → transfer to human if needed)

## Verification Checklist

- [ ] User can send messages in User Web
- [ ] AI placeholder response appears
- [ ] "转人工" keyword triggers human transfer
- [ ] System message "正在为您转接人工客服" appears
- [ ] Agent dashboard shows the active session
- [ ] Agent can see full conversation history
- [ ] Agent can send replies that appear in User Web in real-time
- [ ] Tool panel buttons work (帖子查询 returns mock data)
- [ ] Messages persist in database (check with `psql -d chatbot -c "SELECT * FROM message;"`)
- [ ] GetStream channel shows all participants

## Database Inspection

```bash
# View conversations
psql -d chatbot -c "SELECT * FROM conversation;"

# View sessions
psql -d chatbot -c "SELECT session_id, conversation_id, status, last_activity_at FROM session;"

# View messages
psql -d chatbot -c "SELECT sender_type, sender_id, content, created_at FROM message ORDER BY created_at;"

# View mock post data
psql -d chatbot -c "SELECT * FROM user_post WHERE username='user_alice';"
```

## Troubleshooting

**User Web doesn't load:**
- Check frontend is running: `lsof -i :3000`
- Check browser console for errors

**Messages don't appear:**
- Check backend is running: `curl http://localhost:8080/api/stream/token?userId=test`
- Check GetStream connection in browser Network tab
- Verify API keys in `.env.local` and `frontend/.env`

**Agent Web shows no sessions:**
- Ensure you sent "转人工" in User Web first
- Check session status in database
- Refresh the Agent Web page

## Stop Services

```bash
# Stop backend
kill $(cat /tmp/chatbot-backend.pid)

# Stop frontend
kill $(cat /tmp/chatbot-frontend.pid)
```

## Next Phase

Phase 2 will implement the AI Service Agent with:
- Real intent recognition using Kimi LLM
- ReAct loop for tool orchestration
- FAQ knowledge base with pgvector
- Response composition with evidence tracking
