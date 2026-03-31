# additional scope info

IN SCOPE

Salesforce Enhanced Chat bot on web: 
This means the chatbot will appear on the Gumtree website chat widget (Enhanced Chat), and customers will interact with it there first.
Why it matters: Keeps the first release simple (one channel) and gives you quick learning.


Top 5–10 high-volume intents: 
An “intent” is basically a reason someone is contacting you (e.g., “can’t log in”, “refund”, “report a scam”).
Phase 1 should focus only on the highest volume, repeatable reasons.
Why it matters: Bots succeed when you start small and focus on what drives most contacts.

 Knowledge surfacing (Help Centre articles)
The bot can suggest relevant Help Centre articles during the conversation, based on what the customer selects or types.
This is the main way you: reduce contact, push self-serve, improve speed to answer
Why it matters: It directly supports your objective: use helpdesk pages more, contact agents less.

 Guided flows for triage
This means the bot asks structured questions like: “What do you need help with?” “Is this about a listing, payments, your account, or safety?” “Can you share your email or reference number?”
Instead of free text chaos, you guide customers through a simple decision tree.
Why it matters: It improves routing, improves case quality, and reduces back-and-forth.

 Case creation for defined categories
The bot can create a Salesforce case automatically for certain issues, using the customer’s answers.
Example: “Report a scam” → bot captures key info → creates case → routes to Trust & Safety queue.
Why it matters: Even if the bot can’t solve the problem, it can speed up resolution by removing admin work.

 Escalation to agent via Omni-Channel
If the bot can’t resolve the issue, it hands over to a human agent through Omni-Channel.
Why it matters: This is how you protect customer experience. The bot should never trap people.
Reporting (containment, escalation, CSAT, drop-offs)
This means you’ll set up measurement from day one: what the bot resolved, where customers drop out, how often it escalates, whether customers are happy
Why it matters: Without reporting, the bot won’t improve — and people will lose trust in it fast.



OUT OF SCOPE

Voice botL
This means we are not building anything for phone calls (IVR, voice assistants, etc.). This is chat-only.
Why it’s out: Voice is a totally different build, different customer expectations, and much higher risk.

Social messaging channels (WhatsApp, Facebook Messenger, etc.)
We won’t deploy the bot into external channels in Phase 1.
Why it’s out: Each channel has different setup, compliance, templates, and support processes. Better to prove value on web chat first.

Payments, refunds processing, identity verification
The bot will not handle anything involving: payment processing, refund execution, identity checks (e.g., uploading documents), anything requiring high-trust authentication 
Why it’s out: High fraud risk, high compliance risk, and usually needs strong authentication + strict controls.

Complex disputes / legal complaints / fraud automation
The bot won’t “resolve”: disputes between buyers and sellers, chargeback-style issue, legal complaints, fraud investigations
It can potentially triage and route, but not decide outcomes.
Why it’s out: These are high-stakes issues that require human judgement and consistent policy application.

Open-ended GenAI answering (unless explicitly approved)
This means the bot won’t be a “ChatGPT-style” assistant where customers can ask anything and it generates answers freely.
Instead, Phase 1 should stick to: guided flows, knowledge articles, controlled responses
Why it’s out: Open GenAI introduces risk: hallucinations (wrong answers), policy inconsistency, brand/reputation damage - Especially risky for marketplace safety topics.

Replacing agents / reducing headcount as a goal
The goal is not “remove humans”. The goal is: reduce avoidable contact, speed up resolution, allow agents to focus on complex work
Why it’s out: It creates the wrong internal narrative and usually leads to rushed automation that hurts customers.
Full automation of every enquiry type

Phase 1 will not cover all contact reasons — only a defined set of top intents.

Why it’s out:
Trying to cover everything early is the fastest way to build a bot that fails and annoys customers.

If you want, I can help you add a really strong final line like:

“Items listed as out of scope may be considered for future phases subject to performance, customer feedback, and risk approval.”

That tends to land well with stakeholders.


# user story
A) User Stories (MVP)
Self-serve resolution
As a customer, I want to select what I need help with so that I can get the right answer quickly.
As a customer, I want the bot to suggest relevant Help Centre articles so that I can resolve my issue without waiting for an agent.
As a customer, I want to confirm whether the answer helped so that I can either end the chat or continue.


Escalation
As a customer, I want to be able to request a human agent at any point so that I am not blocked by the bot.
As a customer, I want the agent to understand my issue when I’m transferred so that I don’t need to repeat myself.


Case creation
As a customer, I want the bot to capture the details of my issue so that I can log a support request quickly.
As an agent, I want cases created by the bot to contain structured information so that I can resolve them faster.


Internal experience
As an agent, I want to see the customer’s intent, transcript, and answers so that I can handle the chat efficiently.
As a support lead, I want reporting on bot performance so that we can improve it and ensure it is not harming customer experience.



B) Functional Requirements (MVP)
Bot entry and identification
The bot must be the default first response for in-scope chat traffic (pilot % configurable).


The bot must present a clear menu of support topics (intent selection) and allow free-text where required.


Knowledge and self-service
The bot must surface Help Centre / Salesforce Knowledge content based on selected intent.


The bot must provide links and short summaries (not just “go read this”).


The bot must ask a resolution confirmation question (e.g., “Did this solve your problem?”).


Escalation
The bot must allow escalation to an agent at any point via a visible option (e.g., “Talk to an agent”).


The bot must escalate after:


2 failed intent recognitions, OR


customer requests an agent, OR


customer indicates frustration, OR


the issue falls into an out-of-scope category.


The bot must communicate expected wait time / next steps when escalating.


Bot-to-agent handover
When escalating, the bot must pass the following to the agent:


detected intent + confidence (where available)


customer responses to structured questions


full transcript


any surfaced Help Centre content


case number (if created)


The agent must receive the conversation in the same thread without forcing the customer to restart.


Case creation
The bot must be able to create a Salesforce Case for defined enquiry categories.


The bot must populate mandatory fields including:


contact reason / intent


product area (vertical)


urgency (where applicable)


customer description


relevant identifiers (user ID, listing ID, etc. where available)


Reporting
The solution must capture and report:


bot containment rate


escalation rate and reasons


drop-off rate during bot flow


CSAT for bot and escalated journeys


top intents and failure points



C) UI / Experience Requirements (MVP)
The customer must be clearly informed they are interacting with a bot.


The bot must use concise language aligned with Gumtree tone of voice.


The bot must not force customers through long flows before offering escalation.


The bot must provide a clear “Start again” option.


The bot must work on mobile and desktop.


The bot must support accessibility requirements (basic WCAG-aligned behaviour where feasible).



D) Legal / Compliance Requirements (MVP)
The chatbot must comply with UK GDPR requirements for personal data handling.


The bot must avoid requesting or storing sensitive personal data in free text.


Where identifiers are required (email, case number), the bot must explain why it is being requested.


Chat transcripts must be stored and retained according to Gumtree’s data retention policy.


Any AI-driven responses must be controlled to avoid hallucinations and policy-inaccurate guidance.


The bot must provide appropriate signposting for high-risk topics (fraud, safety, legal complaints), with escalation pathways.



Post-MVP Requirements (Phase 2+)
Expansion of intent coverage beyond the initial 5–10 intents.


Additional automation actions (e.g., status updates, guided troubleshooting, proactive prompts).


Deployment into additional channels (app messaging, WhatsApp, etc.) subject to strategy.


Advanced analytics and automated transcript tagging for continuous improvement.



# business requirement document (main part)
Customer Service - Live Chat Bot
* mandatory for initial draft
In red recommended as essential scope 
In blue what can be refined later.


1. Executive Summary*
Gumtree Customer Support has implemented Salesforce Enhanced Chat and now has the opportunity to introduce a Salesforce-native chatbot capability to improve service efficiency and scale support without increasing operational cost.
The proposed chatbot will act as the first point of contact for customers, handling common enquiries through guided conversations, surfacing relevant Help Centre content, and completing simple support actions where appropriate. Where the chatbot cannot resolve the issue, it will seamlessly transfer the conversation to a live agent, passing key context to reduce repetition and speed up resolution.
This initiative is intended to deliver three core outcomes:
Reduce avoidable customer contact by deflecting repeatable queries to self-service
Improve customer experience through faster access to answers and clearer next steps
Improve support productivity by reducing agent workload and focusing agent time on higher-value or complex cases.
The chatbot will be implemented in a phased approach, starting with low-risk, high-volume use cases and expanding based on performance, customer feedback, and operational impact. Success will be measured through a combination of operational metrics (e.g., deflection/containment, escalation rate, handle time) and customer outcomes (e.g., CSAT, drop-off rate, repeat contact).
This BRD defines the scope, functional requirements, non-functional requirements, experience principles, and measurement approach required to implement the chatbot in a way that supports business goals while protecting customer experience.
2. Problem statement* 
Customer Support currently handles approximately 5.7k chat interactions per month (based on Jan-26 actuals and Feb run-rate). A significant portion of these contacts relate to repeatable enquiries that could be resolved through improved self-service, without requiring a live agent.
While Gumtree UK has Help Centre content available, customers continue to initiate chat rather than finding answers through helpdesk pages, indicating that self-service is not being surfaced effectively at the point of need.
As a result, support demand is higher than necessary, wait times can increase during peak periods, and agent capacity is spent on queries that do not always require human intervention. This reduces overall service efficiency and limits the ability to scale support through digital channels.
2.1 User Problem and its scale: 
User Experience: Customers contact chat for questions that could be answered via Help Centre, but self-serve isn’t being surfaced at the right time, so they default to an agent
Internal: Agents spend time on repeatable, low-complexity queries which reduces capacity for complex cases and increases wait times..
At current volumes (~5.7k chats/month), this equates to ~4.1 FTE of chat capacity when factoring in 2-chat concurrency, 80% occupancy, and 30% shrinkage.
Subject split (Jan26 + Feb RR): 
[add more analytics/data]
2.2 Market context & competitor benchmarks: 
For modern digital marketplaces and consumer platforms, offering AI chat and robust self-service options is now a fundamental requirement. Customers expect instant answers, especially out of hours.
Competitor benchmark examples:
Facebook Marketplace: Heavy self-serve + limited live support
eBay: Strong help centre, automation, status tools
Vinted / Depop: Self-serve-first models
Amazon: High automation + guided flows
Rightmove / Zoopla: Self-serve and form-led support
If Gumtree doesn’t improve self-service, it risks higher cost-to-serve and slower support as demand grows. Poor support experience impacts trust, which matters for a marketplace (buyers/sellers churn).
Chat Automation: Minimum Standard vs. Differentiator:
Minimum Standard: Implementing chat automation is essential and expected (‘table stakes’).
Differentiator & CX Protection: A bot that effectively resolves issues and ensures a smooth handoff to a human agent is what sets a service apart, preventing negative Customer Experience (CX) damage.
1.3 Scope: 
In Scope (Phase 1): 
Salesforce Enhanced Chat bot on web (and/or app if relevant)
Top 5–10 high-volume contact reasons.
Knowledge surfacing (Help Centre articles)
Guided flows for triage (automated responses, based on user queries) 
Case creation for defined categories
Escalation to agent via Omni-Channel
Reporting (containment, escalation, CSAT, drop-offs)
Out of scope (Phase 1): 
Voice bot
Social messaging channels (WhatsApp etc.) 
Payments, refunds processing, identity verification
Complex disputes, legal complaints, fraud escalation automation
Open-ended GenAI answering
Replacing agents 
Items listed as out of scope may be considered for future phases subject to performance, customer feedback, and risk approval
3. Business Goals & Expected Impact*
3.1 Business Goals & Objectives: 

The objective of implementing a Salesforce chatbot is to reduce avoidable customer contact and improve service efficiency by increasing successful self-service resolution through the Help Centre, while maintaining or improving customer experience.
This initiative supports Gumtree UK’s broader goals of improving operational efficiency, scaling customer support sustainably, and strengthening customer trust through faster and more consistent service outcomes.
Measurable outcomes (Phase 1–2):
Reduce agent-handled chat volume by increasing self-service resolution for defined high-volume enquiries.
Increase usage of Help Centre content at the point of need (via bot surfacing and guided flows).
Reduce average time to resolution for customers with simple queries.
Improve agent efficiency by reducing time spent on repetitive enquiries and improving case quality through structured triage.
Maintain or improve customer satisfaction (CSAT) for chat journeys, including bot-to-agent handover.
Cost-to-serve reduction.
Response time and FTR


Where available, these will be aligned to relevant Customer Support / CX OKRs (e.g., cost-to-serve reduction, faster response times, improved CSAT, digital-first support)
3.2 Commercial Impact: 
Gumtree UK currently receives approximately 5.7k chat interactions per month, equating to an estimated ~4.1 FTE of chat capacity (~£65K per year) based on:
Average handle time (AHT): 469 seconds
Concurrency: 2 chats per agent
Occupancy: 80%
Shrinkage: 30%
A Salesforce chatbot is expected to reduce avoidable agent-handled contacts by resolving a portion of repeatable enquiries through self-service and Help Centre content. This will:
Reduce operational effort required to handle chats
Improve service speed during peak periods
Allow existing agent capacity to be reallocated to complex work
Benefits will be realised through capacity release (not necessarily headcount reduction), enabling Gumtree to handle demand more efficiently without proportional increases in staffing. 
Potential cost savings can be achieved, contingent upon the level of deflection, the quality of the supporting articles, and the ease of use for the user: 

3.3 Impacted Audience and Verticals: 
Users seeking help with account access, listings, and general platform guidance.
Users unable to find answers via the Help Centre
Users contacting during peak periods or out-of-hours
All users across all verticals that require support
Both existing users and new/first-time users will be impacted, particularly those unfamiliar with Gumtree processes who are more likely to contact support.
Internal audience
The initiative will also impact:
Customer Support agents (chat handling, escalations, handovers)
Team leaders / supervisors (queue performance, coaching, quality monitoring)
Knowledge owners (article quality, maintenance, content gaps)
Salesforce admins / operations (configuration, reporting, governance)
4. Requirements & Constraints*
4.1 High-Level Requirements*:
The Salesforce chatbot MVP must deliver the following capabilities:
Provide a chatbot experience within Salesforce Enhanced Chat on Gumtree website.
Identify the customer’s reason for contact through guided flows and/or natural language intent recognition.
Surface relevant Help Centre / Knowledge content to resolve common enquiries without agent involvement.
Support seamless escalation to a live agent via Omni-Channel, with clear customer messaging.
Pass structured context to agents on handover (intent, transcript, customer inputs, and any relevant identifiers).
Create a Salesforce Case for defined enquiry types where self-serve resolution is not possible.
Capture reporting data required to measure containment, escalations, drop-offs, and CSAT impact.
Ensure the chatbot experience does not negatively impact customer satisfaction, particularly for customers needing human support.


4.3 Constraints: 
Timeline constraint: The MVP must be delivered within an agreed timeframe without delaying ongoing support operations.
Operational constraint: The bot must not materially increase contact volume or reduce CSAT during rollout.
Content constraint: The bot is limited by the quality and coverage of Help Centre / Knowledge content.
Technical constraint: Functionality is dependent on Salesforce Enhanced Chat and available Salesforce bot capabilities/licensing.
Resource constraint: Delivery requires availability from Salesforce admins, support operations, and knowledge owners.
Risk constraint: High-risk categories (fraud, disputes, payments) must remain human-led in MVP.

4.4 Assumptions and dependencies:
A portion of current chat demand relates to repeatable enquiries that can be resolved through guided self-service.
Customers will accept bot-first journeys if escalation is simple and fast.
Knowledge content can be improved where gaps are identified during discovery.
Dependencies
Salesforce Enhanced Chat is configured and stable for the website.
Omni-Channel routing, queues, and agent capacity models are defined.
Salesforce Knowledge / Help Centre content is available and maintained.
Reporting and dashboarding capability is in place to measure performance.
Legal/compliance review is completed prior to launch.
Internal training is completed for agents and team leads (handover expectations, workflow changes).
1. Measurement & Scaling Framework
5.1 Success Metrics (KPIs)
Given the potential risk of negatively impacting customer experience, the chatbot should be launched as a controlled rollout with defined go/no-go criteria and an agreed measurement framework. The rollout should be treated as an experiment initially, with traffic gradually increased as performance stabilises and customer impact is validated.
Measurement will focus on two outcomes:
Efficiency and contact reduction (business benefit).
Customer experience protection (must not degrade).


Primary KPIs (success metrics)
These are the metrics used to judge whether the chatbot is delivering the intended business outcomes.
Contact reduction / efficiency
Bot containment rate: % of chat sessions resolved without agent handover.
Agent-handled chat volume: reduction in chats requiring an agent for in-scope intents.
Average handle time (AHT) for agent chats: expected to reduce through better triage and context.
Queue performance: reduction in peak-time backlog / improved response times.
Self-service adoption
Help Centre usage via chat: % of bot sessions where a Help Centre article is surfaced and clicked.
Deflection rate: % of customers who accept a self-serve answer and end the session.


Customer experience
CSAT for ‘bot’ conversations (where captured).
CSAT for escalated conversations (bot → agent journeys).
Drop-off rate: % of customers abandoning during bot flow.
Go / No-Go Launch Criteria
The MVP should not be scaled beyond pilot unless:
CSAT for bot journeys is not materially lower than baseline chat CSAT (e.g., no >X point drop).
Escalated journeys do not show a significant CSAT decline vs standard agent chat.
Drop-off rate remains within an acceptable threshold.
Operational
No increase in repeat contact driven by failed bot journeys.
No increase in misrouted chats/cases.
Agents confirm that handover context is sufficient and reduces repetition.
Note: The exact thresholds (X) should be agreed with Support + Product + Data during BRD/PRD phase.
5.2 Risks and Mitigation







1. Positioning & Messaging


6.1 How users would be impacted by change:
This change impacts customers who contact Gumtree Support via web chat, including:
Users with quick questions who want an immediate answer
Users who cannot find the right Help Centre content
Users contacting during peak times
Users with complex issues who ultimately need an agent
Internal teams:
Customer Service Agents (handover, chat flow, case quality)
Customer Service Management (new performance & quality measures)
Knowledge owners (content gaps become visible)
Positive impact (expected):
Faster access to answers for common questions (reduced waiting)
Clearer self-service journeys and more relevant Help Centre content surfaced at the point of need
Better triage and reduced repetition when escalated to an agent
Improved service consistency (standard answers for common topics)
Potential negative impact (risk):
Users may feel blocked or frustrated if they cannot reach a human quickly
Users may abandon if the bot does not understand their issue
Users may distrust responses if they appear generic or inaccurate
If escalation handover is poor, customers may need to repeat information
These risks will be managed through clear escalation pathways, controlled rollout, and continuous monitoring of customer feedback and CSAT.
6.2 How we want the change to be perceived: 
The chatbot should be perceived as an improvement to support access, not a barrier.
What we want customers to think:
“This is quicker than waiting.”
“I can get an answer straight away.”
“If it’s more complicated, I can still speak to someone.”
What we want customers to feel:
Supported and guided (not blocked)
Confident that the information is correct
Reassured that a human is available when needed
What we want customers to do:
Use self-service for simple issues
Click and use Help Centre content surfaced by the bot
Provide structured information when escalation is required (to speed up resolution)
6.3 Messaging Tips: 
Tone principles:
Friendly, clear, and human - avoid overly “robotic” phrasing
Keep it short - UK users tend to respond better to concise, direct guidance
Be transparent - clearly state it’s an automated assistant
Avoid corporate language (“leveraging”, “delighted”, “we apologise for inconvenience”)
Naming conventions:
Avoid calling it “AI” in the customer UI unless necessary.
Use neutral, helpful naming such as:


“Gumtree Support Assistant”
“Support Assistant”
“Virtual Assistant”
(“Chatbot” is fine internally, but can feel cold externally.)
Phrases that work well:
Opening
“Hi - I’m the Gumtree Support Assistant. I can help with common questions, or connect you to the team.”
Self-service
“Here’s the best article for that - it should sort it in a couple of minutes.”


Resolution check
“Did that answer your question?”


Yes → “Great — anything else I can help with?”
No → “No problem — I’ll connect you to the team.”
Escalation
“I’ll pass this to an agent now. I’ll share what you’ve told me so you don’t need to repeat yourself.”
Out of hours
“The team is currently offline. I can still help with common questions, or log this for follow-up.”
Phrases to avoid
“I understand how you feel” (can sound fake if the bot is wrong)
“I’m sorry to hear that” repeatedly
“As an AI language model…”
Anything implying the bot has taken action unless it has confirmed it (e.g., “I’ve fixed that”)
Consistency across touchpoints
The same language and intent naming should be used across:
Chat UI
Help Centre pages
Contact-us pages
Support forms
Any marketing or comms referencing improved support
7. Go-to-Market (GTM) Plan and Stakeholders
7.1 GTM approach:
The Salesforce chatbot will be launched using a phased rollout approach to minimise customer experience risk and validate performance before scaling. Given the potential for negative impact (e.g., increased frustration, drop-offs, or repeat contact), the launch will initially be treated as an experimental rollout with defined go/no-go criteria.
MVP launch approach:
The MVP will focus on a limited set of high-volume, low-risk intents designed to:
Increase successful self-service resolution through Help Centre content
Reduce avoidable agent-handled contacts
Improve triage quality for chats requiring escalation
The MVP will be rolled out to a small percentage of web chat traffic initially (e.g., 5–10%), then gradually expanded once health metrics and success criteria are met.
Post-MVP approach
Following MVP validation, the chatbot will be expanded to:
Cover additional intents and verticals
Increase automation (e.g., case creation for more categories)
Optimise flows based on transcript analysis and customer feedback
Potentially extend to additional channels/teams (subject to strategy and risk approval)
Marketing / external comms
This change is primarily an operational improvement and does not require a major marketing launch. Customer-facing messaging will be delivered within the chat UI and Help Centre to set expectations and ensure clarity.
Key assumptions to validate
A meaningful portion of chat demand is resolvable through improved self-service and guided flows.
Customers will accept a bot-first experience when escalation to a human is simple and fast.
Help Centre content quality and coverage is sufficient for the prioritised intents (or can be improved pre-launch).
7.2 Key Stakeholders for alignment* and Launch Preparation prior technical readiness for rollout



### blue part start

4.2 Detailed Key Requirements: specify relevant User Stories, Functional, UI and Legal/Compliance requirements and provide specific details for each.
User stories examples:
As a local buyer, I want my postcode to stay saved even if I click a link for an item in another city, so that I don't have to keep re-entering my location.
Functional Requirements example:
If no Saved Preference exists (new user), default to "United Kingdom" and prompt for location via a "Set your location" UI hint.
Legal requirement example:
Capture user consent to share data with 3rd parties. 

5.3 Health Metrics: outline health metrics that indicate acceptable level negative impact (for example CS contact rate, monitoring repeat listings by impacted sellers for churn-risk changes)

