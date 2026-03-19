INSERT INTO legal_term
(term_code, version_num, title, short_text, full_text, scope_type, is_required, display_order, is_active)
VALUES

(
'AIRA_TERMS_PRIVACY',
1,
'Agreement to AIRA Terms and Privacy Policy',
'I have read and agree to the AIRA Terms of Use and Privacy Policy.',
'By creating or using an InteropHub account, you agree to the AIRA Terms of Use and AIRA Privacy Policy, which govern your use of this website and related services. These documents describe permitted and prohibited use, account responsibilities, acceptable use expectations, intellectual property and content rules, privacy practices, communications, data collection, and AIRA''s rights regarding access and enforcement. Please review both documents before continuing: <a href="https://repository.immregistries.org/files/AIRA-Terms_of_Use_2024.pdf" target="_blank" rel="noopener noreferrer">Terms of Use</a> <a href="https://aira.memberclicks.net/assets/docs/Organizational_Docs/AIRA%20Privacy%20Policy%20-%20Final%202024_.pdf" target="_blank" rel="noopener noreferrer">Privacy Policy</a>',
'REGISTRATION',
1,
10,
1
),

(
'TESTING_ONLY_DATA_RESTRICTIONS',
1,
'Testing Use Only and Data Restrictions',
'I understand this is a testing environment and will not use production data or sensitive information.',
'InteropHub and connected systems are provided only for testing, experimentation, demonstration, and Connectathon-related interoperability activities. They are not for production use and must not be relied on for operational, legal, clinical, financial, or regulatory purposes. You must not submit production data, protected health information (PHI), personally identifying information, real credentials, production API keys, confidential business information, or other sensitive or regulated data. Only synthetic, test, or demonstration data may be used. If you inadvertently submit prohibited data, you must notify AIRA promptly.',
'BOTH',
1,
20,
1
),

(
'SYSTEM_LIMITATIONS_LOGGING',
1,
'System Limitations and Activity Logging',
'I understand systems may be unreliable and that my activity will be logged for testing and analysis.',
'You acknowledge that systems available through InteropHub may be incomplete, inaccurate, unavailable, interrupted, reset, changed, or removed at any time without notice. Results may be wrong, interfaces may not match production implementations, and participating systems may not behave consistently. AIRA provides the environment and related content on an "as is" and "as available" basis without warranty. You also understand that AIRA will collect and use account, usage, device, IP, logging, and related activity information to operate the site, support users, verify accounts, improve services, analyze Connectathon activity, troubleshoot issues, enforce policies, and communicate with participants, consistent with the AIRA Privacy Policy.',
'REGISTRATION',
1,
30,
1
),

(
'WORKSPACE_COLLABORATION_SYSTEMS',
1,
'Workspace Collaboration and Third-Party Systems',
'I understand I will interact with systems from other organizations that AIRA does not control or guarantee.',
'By joining a workspace, you acknowledge that a Connectathon is a collaborative testing event involving systems operated by multiple organizations. AIRA may list, describe, or help coordinate access to these systems, but AIRA does not control or guarantee the behavior, accuracy, security, availability, conformity, or fitness of systems operated by other participants. Those systems may be unavailable, may respond incorrectly, may be configured differently than expected, and may change during the event. You are responsible for exercising appropriate caution and for using only test data when interacting with any workspace system.',
'WORKSPACE',
1,
110,
1
),

(
'WORKSPACE_CONTACT_SHARING',
1,
'Participant Visibility and Contact Sharing',
'I agree that my name, organization, and contact information may be shared with approved workspace participants.',
'By requesting or accepting enrollment in a workspace, you consent to AIRA using your registration and workspace participation information to coordinate Connectathon activities and to contact you regarding the workspace. You also agree that, within that workspace, AIRA may share your name, organization, role or title, email address, declared systems, endpoints, and participation or progress information with other approved workspace participants for collaboration, troubleshooting, scheduling, and event execution. This sharing is limited to Connectathon-related coordination and is part of participating in the workspace.',
'WORKSPACE',
1,
120,
1
); 