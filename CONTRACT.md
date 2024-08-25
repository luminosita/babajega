
We are maintaining a chat application based on Conversations source code.

We need preferences dialog to setup unlock PIN and destroy PIN for the application. PIN entry should be requested everytime application is opened or after some period of inactivity. User can put unlock PIN to activate application or destroy PIN to reset the whole application. PINs entry preferences should specify if application is asking for a PIN every time or after a period of inactivity (1,2,3 ... minutes)

We also need preferences dialog to configure list of chat servers application is connecting to. Each server can be specified as a URL (chat-server1.com) or as an IP address (10.20.30.40)

Rough design screens for the changes:
https://www.figma.com/design/xOkU2acZ4RerNtNxyMcexd/PIN-Servers?node-id=0-1&t=K9hAo29HCa87qhVk-1

Source code repository:
https://github.com/luminosita/babajega.git (branch "pin")
