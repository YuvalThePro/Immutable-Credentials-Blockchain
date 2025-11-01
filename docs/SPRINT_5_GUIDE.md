# Development Guide

## Sprint 5: Basic GUI (v0.5.0)

### Objectives
- Create main application window
- Implement credential issuance interface
- Implement credential verification interface

### Tasks
1. **MainWindow.java**
   - Create JFrame with menu bar
   - Add tabbed pane for different panels
   - Create status bar
   - Wire up event handlers

2. **IssueCredentialPanel.java**
   - Create form with JTextFields for:
     * Student Name
     * Degree/Certification
     * Date Awarded
     * Institution
     * Student ID
   - Add "Issue Credential" button
   - Connect to blockchain backend
   - Show success/error messages

3. **VerifyCredentialPanel.java**
   - Create search field (Student ID)
   - Add "Search" button
   - Create results display area
   - Show verification status
   - Display credential details if found

4. **Main.java** (update)
   - Launch GUI on startup
   - Initialize backend services
   - Connect GUI to blockchain

### Testing
- Test GUI layout and usability
- Test credential issuance flow
- Test credential verification
- Test error handling in GUI

### Deliverables
- Functional main window
- Working issuance interface
- Working verification interface
