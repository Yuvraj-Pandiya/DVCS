/**
 * BranchSelectorDemo — demo page for testing BranchSelector component.
 *
 * This page can be used to manually test the BranchSelector component
 * during development. Remove or comment out the route after testing.
 */

import { useState } from 'react'
import BranchSelector from '../components/BranchSelector'

export default function BranchSelectorDemo() {
  const [selectedRef, setSelectedRef] = useState('main')

  // Demo repository - replace with actual repo for testing
  const repoOwner = 'testuser'
  const repoName = 'testrepo'

  return (
    <div className="min-h-screen bg-gray-900 text-gray-200 p-8">
      <div className="max-w-4xl mx-auto">
        <h1 className="text-3xl font-bold mb-8">BranchSelector Demo</h1>

        <div className="bg-gray-800 rounded-lg p-6 mb-6">
          <h2 className="text-xl font-semibold mb-4">Component Test</h2>
          
          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-400 mb-2">
              Repository: {repoOwner}/{repoName}
            </label>
            <BranchSelector
              repoOwner={repoOwner}
              repoName={repoName}
              currentRef={selectedRef}
              onChange={(ref) => {
                console.log('Selected ref:', ref)
                setSelectedRef(ref)
              }}
            />
          </div>

          <div className="mt-6 p-4 bg-gray-700 rounded">
            <p className="text-sm">
              <strong>Current selection:</strong> {selectedRef}
            </p>
          </div>
        </div>

        <div className="bg-gray-800 rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4">Testing Instructions</h2>
          <ul className="list-disc list-inside space-y-2 text-sm text-gray-300">
            <li>Click the branch selector button to open the dropdown</li>
            <li>Type in the search box to filter branches and tags</li>
            <li>Use ↑ and ↓ arrow keys to navigate</li>
            <li>Press Enter to select the highlighted item</li>
            <li>Press Escape to close the dropdown</li>
            <li>Click outside the dropdown to close it</li>
            <li>Verify that branches and tags are grouped separately</li>
            <li>Verify that the current branch shows a checkmark</li>
          </ul>
        </div>
      </div>
    </div>
  )
}
