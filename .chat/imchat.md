# instructions

## background
首先这个目录是用来搭建整个 Chatbot 项目的
Chatbot 项目最终会包括人工客服IM， 以及在这上基础上集成 AI Chatbot。
也就是说，我需要通过这个项目来完整地，呃，搭建一套技术方案。 嗯，首先，人工 客服IM，有一个基础的系统作为模拟。然后这里面我会使用 getStream 来作为这个 底层服务的供应商
后端技术栈使用 Java，前端就使用目前业界比较流行的技术栈就可以。数据库直接使用本地PostgreSQL 就可以
构建 AI Chatbot 是我的核心目标，我需要去验证这个 AI Chatbot 的能力，并且验证如何集成 AI，完成人工 IM 的一个继承
直接在我的laptop上搭建所需的各个服务不使用docker，降低不必要的复杂度
llm使用kimi 模型
后面我会分别人工客服IM ，以及AI chatbot
帮我创建claude.md 文件

##  人工客服 IM 系统 

可以参考我画的这个示意用的架构图，然后来搭建整个系统的框架。 因为我们这个系统的框架中需要做好人工客服im和aichatbot的集成，定义二者是如何协作的。
目前的思路是：
每个用户第一次发起inbound message时，会创建一个新的conversation，后面不论是人工客服回复还是 AI Chatbot 的回复，它们都会被拽进这个，或者说被添加到这个 conversation 当中。 所以它们共享一个 conversation id。只有当一个新的个user发起 inbound message 时，才会创建一个新的 conversation，就会有另外一个 conversation id。
每一次用户发起inbound message 时，叠加在conversation的基础上，都会创建一个新的session ，如果在10分钟内有user 或者 人工客服或者aichatbot的回复，则sessionid保持不变，如果10分钟内没有新的消息，则创建新的session，对应新的sessionid
结合用户和客服的消息输入（以及一些im web端的操作，目前可以简化为消息），去维护一个 session 的状态。 然后呢，结合这个，每次在用户有新的 inbound 的 message 的时候，首先更新一下 session 的状态。然后结合这个状态进行路由，进行 router，router 进行路由，路由会决定用户 message 是否会被转发到 AI Chatbot 还是人工客服。 一开始简便期间，我们认为在同一个session中，user的第一条消息一定会被转发给ai chabot，如果此后 user 一旦触发了转人工客服的动作之后，后续用户所有的 inbound message 就不再会转发到 AI Chatbot，而是给人工客服im。所以说我给的示意图，就是基于以上逻辑，方便去搭建一个框架。当然，你也可以结合自己在这方面的经验，帮助做出一些必要的调整

https://my.feishu.cn/wiki/AfKdwHLCxiD4bpk9fnCcnpIEn5w

## PRD spec 创建
UI设计：
user IM： 需要有一个界面,界面可以参见截图，这一个截图是用户侧的 IM 的页面。
人工客服IM（human service agent）： 需要有一个界面,界面可以参见截图，这一个截图是客服侧的 Web 端页面。
AiChatbot（AI service agent） ：不需要页面，因为是后端进程/服务

功能设计：
下面是人工 客服和 AI Agent/AiChatbot 公共依赖的部分
- 系统初期提供三类tools ，因为三类工具其实在具体实现上可能对应的后端服务就是三类接口。一个是，额，FAQ的知识库。二是一个是用户数据删除。三是一个是用户帖子状态查询。
- FAQ知识库，能够根据用户查询的问题，匹配提前整理好的Question和answer，这里面建议使用，Vector DB。使用某个开源的轻量级的 Vector DB 来验证这个功能就可以
- 是这里面所需要能够另外一个能力是说，当用户提供一个他的用户名时，我们可以去删除他在系统中所有的用户数据。然后呢，这个功能我们只需要提供对应的接口，以及一个 mock 实现。
- 这里面，然后需要有一个，呃，查询用户帖子状态的接口。这里面我是希望你能够 mock 出来一些数据，当然存储在我本地的数据库上面，存在 PostgreSQL 数据库里面。这样的话，用户查询就可以在我本地，通过我们的后端服务，查询已经冒号的这些数据，并且给它一个返回。当然因为这是个mock接口，所以每次查的时候，我只需要验证接口可以跑公用就可以，工具可以正常被调用。
 AI Chatbot：它是不需要通过界面去发送信息的，它只是一个后台的服务。支持这个 AI Chatbot 的，这个 Agent 它的实际上背后有，额，三个不同的 subagent：
- 一个是意图识别
- 另外一个是意图识别完之后结合具体的意图和用户的输入，去进行之前提到的几个工具的编排和调用的
- 然后还有一个是负责对回复的组织优化，组织成一个用户可以理解的方式和格式进行返回
人工客服IM ：在这个项目当中，这是希望模拟一个人工客服的 IM 场景。所以，对于转人工之后的用户的 IM 进线，就将它对接到一个固定的人工客服就好。在本系统中，当router路由模块判断当前的 Session 需要交给人工客服处理的话，将人工客服加入到这个 conversation 中，并将这个 session 对接到人工客服。当然在我们这个项目当中，我就是唯一的人工客服，让我能够在这，此时收到接收到这个 session，并负责后续和user 的im联系和问题处理。
路由模块：路由规则需要依赖于当前 session 关联的一些状态数据，这个是不变的。但是当前可尽量简化。当用户发送了“转人工”这样的字样之后，然后呢，我们就将当前的 session 转接给人工客服。只要用户没有发送“转人工”，那么就依旧保持由 AI chatBot，来处理用户的消息。
基于以上信息 ，帮我生成 项目的 PRD spec  markdown